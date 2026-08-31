/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.keymedicationusage.support;

import com.openhis.health.application.outpatient.financial.feedetail.support.OutpatientFeeDetailLoader;
import com.openhis.health.application.report.feedetail.inpatient.support.InpatientFeeDetailReportQuerySupport;
import com.openhis.health.application.report.keymedicationusage.dto.KeyMedicationUsageSummaryRowDto;
import com.openhis.health.domain.administration.chargeitem.query.KeyMedicationUsageAggQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.KeyMedicationUsageReportConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.KeyDrugType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 重点药品使用统计读路径编排（AS-RPT-011 IF-RPT-KDU-02/03）。
 *
 * <p>解析就诊类型/重点药类型集合；按 AMB/IMP 复用费用明细 scope；按类型调用药典 ID 解析与
 * ChargeItem 汇总。同就诊类型下多重点药类型复用同一 {@code OutpatientFeeDetailPageQuery} 缓存。</p>
 */
@Component
@RequiredArgsConstructor
public class KeyMedicationUsageReportQuerySupport {

    /** 门诊费用明细 Payment 日窗 scope。 */
    private final OutpatientFeeDetailLoader outpatientFeeDetailLoader;

    /** 住院费用明细 ChargeItem-only scope。 */
    private final InpatientFeeDetailReportQuerySupport inpatientFeeDetailReportQuerySupport;

    /** 药典重点药类型 → 主键。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 费用明细 scope 上按 productId 汇总。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 校验并解析业务日闭区间。
     *
     * @param dateFromText 起日文案
     * @param dateToText   止日文案
     * @return [from, to]
     * @throws BusinessException 日期非法 → EX-RPT-KDU-102
     */
    public LocalDate[] requireDateWindow(String dateFromText, String dateToText) {
        // Gate：起止日均必填
        if (StringUtils.isEmpty(dateFromText) || StringUtils.isEmpty(dateToText)) {
            throw new BusinessException(
                KeyMedicationUsageReportConstants.EX_RPT_KDU_102,
                "请选择有效查询日期");
        }
        LocalDate dateFrom;
        LocalDate dateTo;
        try {
            dateFrom = LocalDate.parse(dateFromText.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            dateTo = LocalDate.parse(dateToText.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        } catch (DateTimeParseException parseException) {
            // Gate：非法日期格式 → 102
            throw new BusinessException(
                KeyMedicationUsageReportConstants.EX_RPT_KDU_102,
                "请选择有效查询日期",
                parseException);
        }
        // Gate：止日不得早于起日
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException(
                KeyMedicationUsageReportConstants.EX_RPT_KDU_102,
                "请选择有效查询日期");
        }
        return new LocalDate[] {dateFrom, dateTo};
    }

    /**
     * 解析就诊类型集合：空=AMB+IMP；非法码忽略；保持 AMB→IMP 顺序。
     *
     * @param encounterClassCodes 请求多选
     * @return 非空有序集合
     */
    public List<EncounterClass> resolveEncounterClasses(List<String> encounterClassCodes) {
        LinkedHashSet<EncounterClass> resolved = new LinkedHashSet<>();
        if (encounterClassCodes != null) {
            // Loop：仅收 AMB/IMP，忽略空白与未知码
            for (String rawCode : encounterClassCodes) {
                // 空白项跳过
                if (StringUtils.isEmpty(rawCode)) {
                    continue;
                }
                EncounterClass encounterClass = EnumUtil.fromCode(EncounterClass.class, rawCode.trim());
                // 本报表只支持门诊/住院
                if (encounterClass == EncounterClass.AMB || encounterClass == EncounterClass.IMP) {
                    resolved.add(encounterClass);
                }
            }
        }
        // 空筛选 = 全部（BR-RPT-KDU-002）
        if (resolved.isEmpty()) {
            resolved.add(EncounterClass.AMB);
            resolved.add(EncounterClass.IMP);
        }
        List<EncounterClass> ordered = new ArrayList<>();
        // Asm：固定 AMB → IMP 输出序
        if (resolved.contains(EncounterClass.AMB)) {
            ordered.add(EncounterClass.AMB);
        }
        if (resolved.contains(EncounterClass.IMP)) {
            ordered.add(EncounterClass.IMP);
        }
        return ordered;
    }

    /**
     * 解析重点药类型集合：空=三类全开；非法码忽略；按枚举 sort 序。
     *
     * @param keyDrugTypeCodes 请求多选
     * @return 非空有序集合
     */
    public List<KeyDrugType> resolveKeyDrugTypes(List<String> keyDrugTypeCodes) {
        LinkedHashSet<KeyDrugType> resolved = new LinkedHashSet<>();
        if (keyDrugTypeCodes != null) {
            // Loop：映射 KeyDrugType，忽略未知码
            for (String rawCode : keyDrugTypeCodes) {
                // 空白项跳过
                if (StringUtils.isEmpty(rawCode)) {
                    continue;
                }
                KeyDrugType keyDrugType = EnumUtil.fromCode(KeyDrugType.class, rawCode.trim());
                // 未知枚举码不纳入
                if (keyDrugType != null) {
                    resolved.add(keyDrugType);
                }
            }
        }
        // 空筛选 = 全部三类
        if (resolved.isEmpty()) {
            resolved.add(KeyDrugType.BASIC);
            resolved.add(KeyDrugType.NATIONAL_MONITOR);
            resolved.add(KeyDrugType.CENTRAL_PURCHASE);
        }
        List<KeyDrugType> ordered = new ArrayList<>();
        // Asm：固定基药 → 监控 → 集采
        if (resolved.contains(KeyDrugType.BASIC)) {
            ordered.add(KeyDrugType.BASIC);
        }
        if (resolved.contains(KeyDrugType.NATIONAL_MONITOR)) {
            ordered.add(KeyDrugType.NATIONAL_MONITOR);
        }
        if (resolved.contains(KeyDrugType.CENTRAL_PURCHASE)) {
            ordered.add(KeyDrugType.CENTRAL_PURCHASE);
        }
        return ordered;
    }

    /**
     * 构建就诊类型下拉（门诊/住院）。
     *
     * @return OptionDto 列表
     */
    public List<OptionDto> buildEncounterClassOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toOption(EncounterClass.AMB.getCode(), EncounterClass.AMB.getDisplay()));
        options.add(this.toOption(EncounterClass.IMP.getCode(), EncounterClass.IMP.getDisplay()));
        return options;
    }

    /**
     * 构建重点药类型下拉。
     *
     * @return OptionDto 列表
     */
    public List<OptionDto> buildKeyDrugTypeOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toOption(KeyDrugType.BASIC.getCode(), KeyDrugType.BASIC.getDisplay()));
        options.add(this.toOption(
            KeyDrugType.NATIONAL_MONITOR.getCode(), KeyDrugType.NATIONAL_MONITOR.getDisplay()));
        options.add(this.toOption(
            KeyDrugType.CENTRAL_PURCHASE.getCode(), KeyDrugType.CENTRAL_PURCHASE.getDisplay()));
        return options;
    }

    /**
     * 按日窗与类型集合汇总矩阵行（含全 0 行）。
     *
     * @param dateFrom         起日
     * @param dateTo           止日
     * @param encounterClasses 就诊类型有序集合
     * @param keyDrugTypes     重点药类型有序集合
     * @return 矩阵行
     */
    public List<KeyMedicationUsageSummaryRowDto> summarizeMatrixRows(
        LocalDate dateFrom,
        LocalDate dateTo,
        List<EncounterClass> encounterClasses,
        List<KeyDrugType> keyDrugTypes) {
        List<KeyMedicationUsageSummaryRowDto> rows = new ArrayList<>();
        // Loop：先门诊后住院；同类型下复用一次费用明细 scope + 单次矩阵遍历
        for (EncounterClass encounterClass : encounterClasses) {
            OutpatientFeeDetailPageQuery feeDetailQuery =
                this.prepareFeeDetailScope(dateFrom, dateTo, encounterClass);
            Map<String, Collection<Long>> productIdsByBucketKey = new LinkedHashMap<>();
            for (KeyDrugType keyDrugType : keyDrugTypes) {
                productIdsByBucketKey.put(
                    keyDrugType.getCode(),
                    this.medicationDefinitionQueryService.listIdsByKeyDrugType(keyDrugType.getCode()));
            }
            Map<String, KeyMedicationUsageAggQueryModel> aggByKey =
                this.chargeItemQueryService.summarizeKeyMedicationUsageMatrix(
                    feeDetailQuery, productIdsByBucketKey);
            for (KeyDrugType keyDrugType : keyDrugTypes) {
                rows.add(this.summarizeOneRowFromAgg(encounterClass, keyDrugType, aggByKey));
            }
        }
        return rows;
    }

    /**
     * 准备门诊或住院费用明细汇总 scope（仅药品大类）。
     *
     * @param dateFrom       起日
     * @param dateTo         止日
     * @param encounterClass AMB/IMP
     * @return 已写入 links/scope 的 Query
     */
    private OutpatientFeeDetailPageQuery prepareFeeDetailScope(
        LocalDate dateFrom,
        LocalDate dateTo,
        EncounterClass encounterClass) {
        OutpatientFeeDetailPageQuery query = new OutpatientFeeDetailPageQuery();
        query.setBillDateFrom(dateFrom);
        query.setBillDateTo(dateTo);
        query.setTradeDirection(OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL);
        query.setFeeCategoryCodes(List.of(OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION));
        query.setSkipYbFundAmountAttach(Boolean.TRUE);
        // Pre：门诊走 Payment bill_date；住院走 ChargeItem entered_date（对齐费用明细）
        if (encounterClass == EncounterClass.AMB) {
            this.outpatientFeeDetailLoader.prepareOutpatientFeeDetailSummarizeScopesFromChargeBill(query);
        } else {
            // Pre：IMP（及本方法仅接收的非 AMB）走住院费用明细账单轴
            this.inpatientFeeDetailReportQuerySupport
                .prepareInpatientFeeDetailSummarizeScopesFromChargeBill(query);
        }
        return query;
    }

    /**
     * 由矩阵汇总结果装配单行 DTO。
     *
     * @param encounterClass 就诊类型
     * @param keyDrugType    重点药类型
     * @param aggByKey       分桶汇总（key = 重点药类型 code）
     * @return 汇总行（无命中时为 0）
     */
    private KeyMedicationUsageSummaryRowDto summarizeOneRowFromAgg(
        EncounterClass encounterClass,
        KeyDrugType keyDrugType,
        Map<String, KeyMedicationUsageAggQueryModel> aggByKey) {
        KeyMedicationUsageSummaryRowDto row = new KeyMedicationUsageSummaryRowDto();
        row.setEncounterClassCode(encounterClass.getCode());
        row.setEncounterClassDisplay(encounterClass.getDisplay());
        row.setKeyDrugTypeCode(keyDrugType.getCode());
        row.setKeyDrugTypeDisplay(keyDrugType.getDisplay());
        KeyMedicationUsageAggQueryModel agg = null;
        if (aggByKey != null) {
            agg = aggByKey.get(keyDrugType.getCode());
        }
        long encounterNetCount = 0L;
        BigDecimal saleAmountTotal = MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        if (agg != null) {
            if (agg.getEncounterNetCount() != null) {
                encounterNetCount = agg.getEncounterNetCount();
            }
            if (agg.getSaleAmountTotal() != null) {
                saleAmountTotal = agg.getSaleAmountTotal();
            }
        }
        row.setEncounterNetCount(encounterNetCount);
        row.setSaleAmountTotal(saleAmountTotal);
        return row;
    }

    /**
     * 构造下拉选项。
     *
     * @param value 码
     * @param label 文案
     * @return OptionDto
     */
    private OptionDto toOption(String value, String label) {
        OptionDto option = new OptionDto();
        option.setValue(value);
        option.setLabel(label);
        return option;
    }
}
