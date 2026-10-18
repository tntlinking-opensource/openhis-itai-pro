/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.keymedicationusage.support;

import com.zxplat.application.outpatient.financial.feedetail.support.OutpatientFeeDetailLoader;
import com.zxplat.application.report.feedetail.inpatient.support.InpatientFeeDetailReportQuerySupport;
import com.zxplat.application.report.keymedicationusage.dto.KeyMedicationUsageSummaryRowDto;
import com.zxplat.domain.administration.chargeitem.query.KeyMedicationUsageLineQueryModel;
import com.zxplat.domain.administration.chargeitem.query.KeyMedicationUsageAggQueryModel;
import com.zxplat.domain.administration.chargeitem.query.KeyMedicationUsageSectionAggQueryModel;
import com.zxplat.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.medication.medicationdefinition.queries.model.MedicationKeyDrugFlagQueryModel;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.KeyMedicationUsageReportConstants;
import com.zxplat.domain.share.constants.OutpatientFeeDetailConstants;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.KeyDrugType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 重点药品使用统计读路径编排（AS-RPT-011 IF-RPT-KDU-02/03）。
 *
 * <p>解析就诊类型/重点药类型；按 AMB/IMP 复用费用明细 scope；对每个就诊类型按重点药类型
 * 拆行输出人次/金额，并以<strong>全部药典药品</strong>（西药/中成药等）为分母回填人次占比与金额占比；
 * 块末追加「合计」行。</p>
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
     * 解析矩阵行类型：重点药类型码 ∪ 药品性质码；两侧皆空=七类全开。
     *
     * <p>两框独立：只选重点药不自动带麻/精行；只选药品性质不自动带四旗行；皆选则并集。</p>
     *
     * @param keyDrugTypeCodes      重点药类型多选（四旗）
     * @param medicinePropertyCodes 药品性质多选（麻/精一/精二 KeyDrugType code）
     * @return 非空有序集合
     */
    public List<KeyDrugType> resolveKeyDrugTypes(
        List<String> keyDrugTypeCodes,
        List<String> medicinePropertyCodes) {
        LinkedHashSet<KeyDrugType> resolved = new LinkedHashSet<>();
        boolean hasKeyDrugSelection = this.appendResolvedKeyDrugTypes(resolved, keyDrugTypeCodes, true);
        boolean hasMedicinePropertySelection =
            this.appendResolvedKeyDrugTypes(resolved, medicinePropertyCodes, false);
        // 两侧皆空 = 全部七类
        if (!hasKeyDrugSelection && !hasMedicinePropertySelection) {
            resolved.add(KeyDrugType.BASIC);
            resolved.add(KeyDrugType.NATIONAL_MONITOR);
            resolved.add(KeyDrugType.CENTRAL_PURCHASE);
            resolved.add(KeyDrugType.ANTIBIOTIC);
            resolved.add(KeyDrugType.NARCOTIC);
            resolved.add(KeyDrugType.PSYCH_ONE);
            resolved.add(KeyDrugType.PSYCH_TWO);
        }
        return this.orderKeyDrugTypes(resolved);
    }

    /**
     * 将请求码列表写入集合；{@code flagTypesOnly=true} 仅收四旗，否则仅收麻/精。
     *
     * @param target         目标集合
     * @param rawCodes       请求码
     * @param flagTypesOnly  true=仅基药/监控/集采/抗生素
     * @return 是否至少写入一项有效码
     */
    private boolean appendResolvedKeyDrugTypes(
        LinkedHashSet<KeyDrugType> target,
        List<String> rawCodes,
        boolean flagTypesOnly) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            return false;
        }
        boolean any = false;
        for (String rawCode : rawCodes) {
            if (StringUtils.isEmpty(rawCode)) {
                continue;
            }
            KeyDrugType keyDrugType = EnumUtil.fromCode(KeyDrugType.class, rawCode.trim());
            if (keyDrugType == null) {
                continue;
            }
            boolean isFlagType = keyDrugType == KeyDrugType.BASIC
                || keyDrugType == KeyDrugType.NATIONAL_MONITOR
                || keyDrugType == KeyDrugType.CENTRAL_PURCHASE
                || keyDrugType == KeyDrugType.ANTIBIOTIC;
            boolean isMedicineProperty = keyDrugType == KeyDrugType.NARCOTIC
                || keyDrugType == KeyDrugType.PSYCH_ONE
                || keyDrugType == KeyDrugType.PSYCH_TWO;
            // 重点药框只收四旗；药品性质框只收麻/精
            if (flagTypesOnly && isFlagType) {
                target.add(keyDrugType);
                any = true;
            }
            if (!flagTypesOnly && isMedicineProperty) {
                target.add(keyDrugType);
                any = true;
            }
        }
        return any;
    }

    /**
     * 固定输出序：基药 → 监控 → 集采 → 抗生素 → 麻醉 → 精一 → 精二。
     *
     * @param resolved 已解析集合
     * @return 有序列表
     */
    private List<KeyDrugType> orderKeyDrugTypes(LinkedHashSet<KeyDrugType> resolved) {
        List<KeyDrugType> ordered = new ArrayList<>();
        if (resolved.contains(KeyDrugType.BASIC)) {
            ordered.add(KeyDrugType.BASIC);
        }
        if (resolved.contains(KeyDrugType.NATIONAL_MONITOR)) {
            ordered.add(KeyDrugType.NATIONAL_MONITOR);
        }
        if (resolved.contains(KeyDrugType.CENTRAL_PURCHASE)) {
            ordered.add(KeyDrugType.CENTRAL_PURCHASE);
        }
        if (resolved.contains(KeyDrugType.ANTIBIOTIC)) {
            ordered.add(KeyDrugType.ANTIBIOTIC);
        }
        if (resolved.contains(KeyDrugType.NARCOTIC)) {
            ordered.add(KeyDrugType.NARCOTIC);
        }
        if (resolved.contains(KeyDrugType.PSYCH_ONE)) {
            ordered.add(KeyDrugType.PSYCH_ONE);
        }
        if (resolved.contains(KeyDrugType.PSYCH_TWO)) {
            ordered.add(KeyDrugType.PSYCH_TWO);
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
     * 构建重点药类型下拉（仅基药/监控/集采/抗生素；麻/精见 {@link #buildMedicinePropertyOptions}）。
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
        options.add(this.toOption(KeyDrugType.ANTIBIOTIC.getCode(), KeyDrugType.ANTIBIOTIC.getDisplay()));
        return options;
    }

    /**
     * 构建药品性质下拉（仅麻醉 / 一类精神 / 二类精神；与重点药类型分框）。
     *
     * @return OptionDto 列表（value=KeyDrugType code）
     */
    public List<OptionDto> buildMedicinePropertyOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toOption(KeyDrugType.NARCOTIC.getCode(), KeyDrugType.NARCOTIC.getDisplay()));
        options.add(this.toOption(KeyDrugType.PSYCH_ONE.getCode(), KeyDrugType.PSYCH_ONE.getDisplay()));
        options.add(this.toOption(KeyDrugType.PSYCH_TWO.getCode(), KeyDrugType.PSYCH_TWO.getDisplay()));
        return options;
    }

    /**
     * 按日窗与筛选汇总矩阵行：每个就诊类型下「各重点药类型行 + 全部药品合计行」。
     *
     * <p>组内合计 = 时间窗内<strong>全部药典药品</strong>（西药/中成药等）净人次/售价净额；
     * 类型行人次占比 = 该类型净人次 / 全部药品合计人次；金额占比 = 该类型金额 / 全部药品合计金额；
     * 分项之和不必等于合计（多旗重叠）；合计行两项占比均为 100%。</p>
     *
     * @param dateFrom         起日
     * @param dateTo           止日
     * @param encounterClasses 就诊类型有序集合
     * @param keyDrugTypes     重点药类型有序集合
     * @return 汇总行（含各组合计；无门诊+住院全局合计）
     */
    public List<KeyMedicationUsageSummaryRowDto> summarizeMatrixRows(
        LocalDate dateFrom,
        LocalDate dateTo,
        List<EncounterClass> encounterClasses,
        List<KeyDrugType> keyDrugTypes) {
        List<KeyMedicationUsageSummaryRowDto> rows = new ArrayList<>();
        List<String> bucketKeys = this.buildBucketKeys(keyDrugTypes);
        // Loop：先门诊后住院；同类型下 scope/矩阵只准备一次
        for (EncounterClass encounterClass : encounterClasses) {
            OutpatientFeeDetailPageQuery feeDetailQuery =
                this.prepareFeeDetailScope(dateFrom, dateTo, encounterClass);
            // Pre：轻量扫 links，禁止 RowQueryModel 物化与 payment 快照分母 enrich
            List<KeyMedicationUsageLineQueryModel> usageLines =
                this.chargeItemQueryService.listKeyMedicationUsageLines(feeDetailQuery);
            LinkedHashSet<Long> productIdsInWindow = new LinkedHashSet<>();
            for (KeyMedicationUsageLineQueryModel usageLine : usageLines) {
                if (usageLine != null && usageLine.getProductId() != null) {
                    productIdsInWindow.add(usageLine.getProductId());
                }
            }
            Map<Long, MedicationKeyDrugFlagQueryModel> keyDrugFlagsByProductId =
                this.medicationDefinitionQueryService.mapKeyDrugFlagsByIds(productIdsInWindow);
            Map<Long, Set<String>> bucketKeysByProductId = this.buildBucketKeysByProductId(
                bucketKeys, keyDrugFlagsByProductId);
            KeyMedicationUsageSectionAggQueryModel sectionAgg =
                this.chargeItemQueryService.summarizeKeyMedicationUsageSectionFromLines(
                    usageLines, bucketKeys, bucketKeysByProductId);
            Map<String, KeyMedicationUsageAggQueryModel> matrixAgg = sectionAgg.getMatrixByBucketKey();
            KeyMedicationUsageAggQueryModel allMedicationAgg = sectionAgg.getAllMedicationAgg();
            long sectionEncounterNetCount = 0L;
            BigDecimal sectionSaleAmount = MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
            // 全部药品有命中时写入组内合计分母
            if (allMedicationAgg != null) {
                if (allMedicationAgg.getEncounterNetCount() != null) {
                    sectionEncounterNetCount = allMedicationAgg.getEncounterNetCount();
                }
                if (allMedicationAgg.getSaleAmountTotal() != null) {
                    sectionSaleAmount = allMedicationAgg.getSaleAmountTotal();
                }
            }

            // Loop：输出各类型行，占比相对全部药品合计
            for (KeyDrugType keyDrugType : keyDrugTypes) {
                // 空类型跳过（解析结果不应出现）
                if (keyDrugType == null) {
                    continue;
                }
                KeyMedicationUsageAggQueryModel typeAgg = matrixAgg.get(keyDrugType.getCode());
                KeyMedicationUsageSummaryRowDto typeRow =
                    this.assembleTypeRowWithoutRatio(encounterClass, keyDrugType, typeAgg);
                this.applySectionRatios(typeRow, sectionEncounterNetCount, sectionSaleAmount);
                rows.add(typeRow);
            }
            // Asm：本组末追加「合计」行（全部药品人次/金额；占比 100%）
            rows.add(this.assembleSectionTotalRow(
                encounterClass, sectionEncounterNetCount, sectionSaleAmount));
        }
        return rows;
    }

    /**
     * 将重点药类型枚举转为矩阵分桶键（固定输出序）。
     *
     * @param keyDrugTypes 重点药类型集合
     * @return 类型 code 列表
     */
    private List<String> buildBucketKeys(List<KeyDrugType> keyDrugTypes) {
        List<String> bucketKeys = new ArrayList<>();
        if (keyDrugTypes == null || keyDrugTypes.isEmpty()) {
            return bucketKeys;
        }
        for (KeyDrugType keyDrugType : keyDrugTypes) {
            if (keyDrugType != null) {
                bucketKeys.add(keyDrugType.getCode());
            }
        }
        return bucketKeys;
    }

    /**
     * 按窗内药典四旗解析 productId → 命中的类型分桶键（禁止全库 listIdsByKeyDrugType）。
     *
     * @param bucketKeys               矩阵分桶键
     * @param keyDrugFlagsByProductId  窗内 productId 四旗
     * @return productId → 命中类型 code 集合
     */
    private Map<Long, Set<String>> buildBucketKeysByProductId(
        List<String> bucketKeys,
        Map<Long, MedicationKeyDrugFlagQueryModel> keyDrugFlagsByProductId) {
        Map<Long, Set<String>> bucketKeysByProductId = new LinkedHashMap<>();
        if (bucketKeys == null || bucketKeys.isEmpty()
            || keyDrugFlagsByProductId == null || keyDrugFlagsByProductId.isEmpty()) {
            return bucketKeysByProductId;
        }
        for (Map.Entry<Long, MedicationKeyDrugFlagQueryModel> entry : keyDrugFlagsByProductId.entrySet()) {
            Long productId = entry.getKey();
            MedicationKeyDrugFlagQueryModel flags = entry.getValue();
            if (productId == null || flags == null) {
                continue;
            }
            Set<String> matched = new HashSet<>();
            for (String bucketKey : bucketKeys) {
                if (flags.matchesKeyDrugType(bucketKey)) {
                    matched.add(bucketKey);
                }
            }
            if (!matched.isEmpty()) {
                bucketKeysByProductId.put(productId, matched);
            }
        }
        return bucketKeysByProductId;
    }

    /**
     * 装配单重点药类型汇总行（人次/金额；占比由 {@link #applySectionRatios} 按全部药品合计回填）。
     *
     * @param encounterClass 就诊类型
     * @param keyDrugType    重点药类型
     * @param typeAgg        该类型矩阵汇总
     * @return 类型行（占比暂空）
     */
    private KeyMedicationUsageSummaryRowDto assembleTypeRowWithoutRatio(
        EncounterClass encounterClass,
        KeyDrugType keyDrugType,
        KeyMedicationUsageAggQueryModel typeAgg) {
        KeyMedicationUsageSummaryRowDto row = new KeyMedicationUsageSummaryRowDto();
        row.setEncounterClassCode(encounterClass.getCode());
        row.setEncounterClassDisplay(encounterClass.getDisplay());
        row.setKeyDrugTypeCode(keyDrugType.getCode());
        row.setKeyDrugTypeDisplay(keyDrugType.getDisplay());

        long encounterNetCount = 0L;
        BigDecimal saleAmountTotal = MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        // 类型汇总有命中时写入人次/金额
        if (typeAgg != null) {
            if (typeAgg.getEncounterNetCount() != null) {
                encounterNetCount = typeAgg.getEncounterNetCount();
            }
            if (typeAgg.getSaleAmountTotal() != null) {
                saleAmountTotal = typeAgg.getSaleAmountTotal();
            }
        }
        row.setEncounterNetCount(encounterNetCount);
        row.setSaleAmountTotal(saleAmountTotal);
        return row;
    }

    /**
     * 按组内<strong>全部药典药品</strong>合计回填类型行人次占比与金额占比。
     *
     * <p>同人同次开多类重点药时，分项人次之和可大于合计；各类型占比之和亦可大于 100%。</p>
     *
     * @param typeRow                   类型行
     * @param sectionEncounterNetCount  全部药品合计人次（分母）
     * @param sectionSaleAmount         全部药品合计金额（分母）
     */
    private void applySectionRatios(
        KeyMedicationUsageSummaryRowDto typeRow,
        long sectionEncounterNetCount,
        BigDecimal sectionSaleAmount) {
        // Gate：空行不回填
        if (typeRow == null) {
            return;
        }
        long typeCount = this.nullToZeroLong(typeRow.getEncounterNetCount());
        BigDecimal typeAmount = typeRow.getSaleAmountTotal();
        if (typeAmount == null) {
            typeAmount = MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        }
        // 人次占比 = 类型人次 / 全部药品合计人次
        typeRow.setUsageRatioPercent(
            this.computeCountRatioPercent(typeCount, sectionEncounterNetCount));
        // 金额占比 = 类型金额 / 全部药品合计金额
        typeRow.setSaleAmountRatioPercent(
            this.computeAmountRatioPercent(typeAmount, sectionSaleAmount));
    }

    /**
     * 装配某就诊类型块末「合计」行（人次/金额=全部药典药品；占比均为 100%）。
     *
     * @param encounterClass           就诊类型（合计行仍写 AMB/IMP）
     * @param sectionEncounterNetCount 全部药品净人次
     * @param sectionSaleAmount        全部药品售价净额
     * @return 组内合计行
     */
    private KeyMedicationUsageSummaryRowDto assembleSectionTotalRow(
        EncounterClass encounterClass,
        long sectionEncounterNetCount,
        BigDecimal sectionSaleAmount) {
        KeyMedicationUsageSummaryRowDto totalRow = new KeyMedicationUsageSummaryRowDto();
        totalRow.setEncounterClassCode(encounterClass.getCode());
        totalRow.setEncounterClassDisplay(encounterClass.getDisplay());
        totalRow.setKeyDrugTypeCode(KeyMedicationUsageReportConstants.SUMMARY_KEY_DRUG_TYPE_TOTAL);
        totalRow.setKeyDrugTypeDisplay(
            KeyMedicationUsageReportConstants.SUMMARY_KEY_DRUG_TYPE_TOTAL_DISPLAY);
        totalRow.setEncounterNetCount(sectionEncounterNetCount);
        totalRow.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(sectionSaleAmount));
        // 合计相对自身：有数则 100%，无数则 —
        if (sectionEncounterNetCount > 0L) {
            totalRow.setUsageRatioPercent(
                BigDecimal.valueOf(100L).setScale(
                    KeyMedicationUsageReportConstants.USAGE_RATIO_PERCENT_SCALE,
                    RoundingMode.HALF_UP));
        } else {
            totalRow.setUsageRatioPercent(null);
        }
        if (sectionSaleAmount != null && sectionSaleAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalRow.setSaleAmountRatioPercent(
                BigDecimal.valueOf(100L).setScale(
                    KeyMedicationUsageReportConstants.USAGE_RATIO_PERCENT_SCALE,
                    RoundingMode.HALF_UP));
        } else {
            totalRow.setSaleAmountRatioPercent(null);
        }
        return totalRow;
    }

    /**
     * 人次占比：分子人次 / 合计人次 × 100；分母为 0 返回 null。
     *
     * @param numeratorCount 类型净人次
     * @param sectionCount   本组合计人次
     * @return 百分比；分母 0 为 null
     */
    private BigDecimal computeCountRatioPercent(long numeratorCount, long sectionCount) {
        // Gate：合计人次为 0 → 占比不可算
        if (sectionCount <= 0L) {
            return null;
        }
        return BigDecimal.valueOf(numeratorCount)
            .multiply(BigDecimal.valueOf(100L))
            .divide(
                BigDecimal.valueOf(sectionCount),
                KeyMedicationUsageReportConstants.USAGE_RATIO_PERCENT_SCALE,
                RoundingMode.HALF_UP);
    }

    /**
     * 金额占比：分子金额 / 合计金额 × 100；分母为 0 或 null 返回 null。
     *
     * @param numeratorAmount 类型售价净额
     * @param sectionAmount   本组合计售价净额
     * @return 百分比；分母无效为 null
     */
    private BigDecimal computeAmountRatioPercent(BigDecimal numeratorAmount, BigDecimal sectionAmount) {
        // Gate：合计金额空或 ≤0 → 占比不可算
        if (sectionAmount == null || sectionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal safeNumerator = numeratorAmount;
        if (safeNumerator == null) {
            safeNumerator = BigDecimal.ZERO;
        }
        return safeNumerator
            .multiply(BigDecimal.valueOf(100L))
            .divide(
                sectionAmount,
                KeyMedicationUsageReportConstants.USAGE_RATIO_PERCENT_SCALE,
                RoundingMode.HALF_UP);
    }

    /**
     * Long 空安全转 long（null → 0）。
     *
     * @param value 可空 Long
     * @return 非空 long
     */
    private long nullToZeroLong(Long value) {
        if (value == null) {
            return 0L;
        }
        return value;
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
        // 重点药品仍按自然日入参；扩成全日时刻闭区间以对齐费用明细 LocalDateTime 窗
        query.setBillDateFrom(dateFrom.atStartOfDay());
        query.setBillDateTo(dateTo.atTime(23, 59, 59));
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
