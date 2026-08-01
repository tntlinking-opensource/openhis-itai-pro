/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.assembler;

import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageDetailLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageSummaryLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageTotalsDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.support.ChargePricingView;
import com.openhis.health.domain.medication.medicationdispense.queries.model.InpatientPatientMedicationUsageDispenseRow;
import com.openhis.health.domain.share.constants.InpatientPharmacyPatientMedicationUsageConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院患者药品使用明细/汇总/合计装配（AS-IP-PHA-005 · IF-IP-PMU-03）。
 *
 * <p>职责：三态映射、明细账单原样、按药品净额汇总；不承载 HTTP 契约与领域查询。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientPatientMedicationUsageAssembler {

    /** 汇总包装/拆零净数量换算。 */
    private final InpatientPatientMedicationUsageQuantityAssembler quantityAssembler;

    /**
     * 领域发放状态 → 展示三态 code（待发/已发/已退）。
     *
     * @param statusEnum 领域 {@link DispenseStatus#getCode()}；可空
     * @return {@code PENDING}/{@code DISPENSED}/{@code RETURNED}；无法映射时 null
     */
    public String mapUsageStatusCode(String statusEnum) {
        // 空状态无法映射三态，调用方展示为空
        if (StringUtils.isEmpty(statusEnum)) {
            return null;
        }
        String code = statusEnum.trim();
        // 待发：待配/已配/待发/已汇总/部分发（含已汇总未核发，AS §1.3）
        if (DispenseStatus.PREPARATION.getCode().equals(code)
            || DispenseStatus.PREPARED.getCode().equals(code)
            || DispenseStatus.IN_PROGRESS.getCode().equals(code)
            || DispenseStatus.SUMMARIZED.getCode().equals(code)
            || DispenseStatus.PART_COMPLETED.getCode().equals(code)) {
            return InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_PENDING;
        }
        // 已发：仅 COMPLETED
        if (DispenseStatus.COMPLETED.getCode().equals(code)) {
            return InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_DISPENSED;
        }
        // 已退：待退/部分退/已退
        if (DispenseStatus.PENDING_REFUND.getCode().equals(code)
            || DispenseStatus.PART_REFUND.getCode().equals(code)
            || DispenseStatus.REFUNDED.getCode().equals(code)) {
            return InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_RETURNED;
        }
        // 未纳入展示映射的状态（本查询白名单外不应出现）
        return null;
    }

    /**
     * 组装明细行（三态展示、账单原样、计划时间倒序由域查询保证）。
     *
     * @param rows                  发放使用行
     * @param productByKey          产品复合键 → 展示 DTO
     * @param pricingByDispenseId   dispenseId → 账单价
     * @return 明细行列表；入参空时为空列表
     */
    public List<InpatientPatientMedicationUsageDetailLineDto> buildDetailLines(
        List<InpatientPatientMedicationUsageDispenseRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargePricingView> pricingByDispenseId) {
        List<InpatientPatientMedicationUsageDetailLineDto> lines = new ArrayList<>();
        // 无发放事实：返回空明细
        if (rows == null || rows.isEmpty()) {
            return lines;
        }
        // 逐发放行装配明细（时间倒序由域查询保证）
        for (InpatientPatientMedicationUsageDispenseRow row : rows) {
            // 跳过脏行
            if (row == null) {
                continue;
            }
            InpatientPatientMedicationUsageDetailLineDto line = new InpatientPatientMedicationUsageDetailLineDto();
            line.setDispenseId(row.getDispenseId());
            line.setMedicationId(row.getMedicationId());
            ProductDefinitionDisplayDto product = this.resolveProduct(row.getMedicationId(), productByKey);
            // 有产品定义则回填名称/规格
            if (product != null) {
                line.setItemName(product.getProductName());
                line.setItemSpec(product.getTotalVolume());
            }
            line.setQuantityValue(row.getQuantityValue());
            line.setQuantityUnitCode(row.getQuantityUnitCode());
            line.setQuantityUnitDisplay(this.resolveUnitDisplay(row.getQuantityUnitCode(), product));
            ChargePricingView pricing = null;
            // 按发放主键取账单价；缺映射保持空（BR-IP-PMU-012）
            if (pricingByDispenseId != null && row.getDispenseId() != null) {
                pricing = pricingByDispenseId.get(row.getDispenseId());
            }
            // 账单原样写入，不捏造零价
            if (pricing != null) {
                line.setUnitPrice(pricing.getUnitPrice());
                line.setTotalPrice(pricing.getTotalPrice());
            }
            line.setPlannedDispenseTime(row.getPlannedDispenseTime());
            String statusCode = null;
            // 领域枚举转 String code 供前端与三态映射
            if (row.getStatusEnum() != null) {
                statusCode = row.getStatusEnum().getCode();
            }
            line.setStatusEnum(statusCode);
            String usageStatusCode = this.mapUsageStatusCode(statusCode);
            line.setUsageStatusCode(usageStatusCode);
            line.setUsageStatusDisplay(this.mapUsageStatusDisplay(usageStatusCode));
            // 发放类型 code（住院/临时/退药）
            if (row.getDispenseEnum() != null) {
                line.setDispenseEnum(row.getDispenseEnum().getCode());
            }
            line.setSummaryNo(row.getSummaryNo());
            line.setLotNumber(row.getLotNumber());
            lines.add(line);
        }
        return lines;
    }

    /**
     * 按 medicationId 组装汇总行（净数量/净金额；不输出展示发药状态）。
     *
     * @param rows                  发放使用行
     * @param productByKey          产品复合键 → 展示 DTO
     * @param pricingByDispenseId   dispenseId → 账单价
     * @return 汇总行列表；按首次出现药品顺序
     */
    public List<InpatientPatientMedicationUsageSummaryLineDto> buildSummaryLines(
        List<InpatientPatientMedicationUsageDispenseRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargePricingView> pricingByDispenseId) {
        List<InpatientPatientMedicationUsageSummaryLineDto> lines = new ArrayList<>();
        // 无发放事实：返回空汇总
        if (rows == null || rows.isEmpty()) {
            return lines;
        }
        Map<Long, List<InpatientPatientMedicationUsageDispenseRow>> groups = new LinkedHashMap<>();
        // 按 medicationId 保序分组（不按 summary_no 分桶）
        for (InpatientPatientMedicationUsageDispenseRow row : rows) {
            // 无药品主键无法入组
            if (row == null || row.getMedicationId() == null) {
                continue;
            }
            groups.computeIfAbsent(row.getMedicationId(), ignored -> new ArrayList<>()).add(row);
        }
        // 逐药品组轧差净额与净数量
        for (Map.Entry<Long, List<InpatientPatientMedicationUsageDispenseRow>> entry : groups.entrySet()) {
            Long medicationId = entry.getKey();
            List<InpatientPatientMedicationUsageDispenseRow> groupRows = entry.getValue();
            InpatientPatientMedicationUsageSummaryLineDto line = new InpatientPatientMedicationUsageSummaryLineDto();
            line.setMedicationId(medicationId);
            ProductDefinitionDisplayDto product = this.resolveProduct(medicationId, productByKey);
            // 回填名称/规格/包装与拆零单位展示
            if (product != null) {
                line.setItemName(product.getProductName());
                line.setItemSpec(product.getTotalVolume());
                line.setPackageUnitCode(product.getPackageUnitCode());
                line.setPackageUnitDisplay(product.getPackageUnitCodeDictText());
                line.setSplitUnitCode(product.getSplitUnitCode());
                line.setSplitUnitDisplay(product.getSplitUnitCodeDictText());
            }
            BigDecimal lineTotal = BigDecimal.ZERO;
            BigDecimal representativeUnitPrice = null;
            // 组内账单行合计轧差；退药账单通常为负，直接相加即净额
            for (InpatientPatientMedicationUsageDispenseRow groupRow : groupRows) {
                // 缺发放主键或无价表：跳过该行金额
                if (groupRow == null || groupRow.getDispenseId() == null || pricingByDispenseId == null) {
                    continue;
                }
                ChargePricingView pricing = pricingByDispenseId.get(groupRow.getDispenseId());
                // 无账单：不造零参与汇总金额
                if (pricing == null) {
                    continue;
                }
                // 累加行合计净额
                if (pricing.getTotalPrice() != null) {
                    lineTotal = lineTotal.add(pricing.getTotalPrice());
                }
                // 组内任一条有单价即作展示代表（非权威均价）
                if (representativeUnitPrice == null && pricing.getUnitPrice() != null) {
                    representativeUnitPrice = pricing.getUnitPrice();
                }
            }
            line.setUnitPrice(representativeUnitPrice);
            line.setLineTotalAmount(lineTotal);
            InpatientPatientMedicationUsageQuantityAssembler.PackageSplitQuantityResult qtyResult =
                this.quantityAssembler.aggregateNetPackageAndSplitQuantity(groupRows, product);
            line.setPackageQuantity(qtyResult.packageQuantity());
            line.setSplitUnitQuantity(qtyResult.splitUnitQuantity());
            lines.add(line);
        }
        return lines;
    }

    /**
     * 计算药品使用净金额合计；{@code lineCount} 由调用方按当前模式行数回填。
     *
     * @param rows                  发放使用行
     * @param pricingByDispenseId   dispenseId → 账单价
     * @return 合计 DTO；grandTotal 缺价按 0 轧（空结果亦为 0）
     */
    public InpatientPatientMedicationUsageTotalsDto buildTotals(
        List<InpatientPatientMedicationUsageDispenseRow> rows,
        Map<Long, ChargePricingView> pricingByDispenseId) {
        InpatientPatientMedicationUsageTotalsDto totals = new InpatientPatientMedicationUsageTotalsDto();
        BigDecimal grandTotal = BigDecimal.ZERO;
        // 对全部发放行账单合计轧差；与展示模式无关（BR-IP-PMU-008）
        if (rows != null && pricingByDispenseId != null) {
            for (InpatientPatientMedicationUsageDispenseRow row : rows) {
                // 无发放主键无法取价
                if (row == null || row.getDispenseId() == null) {
                    continue;
                }
                ChargePricingView pricing = pricingByDispenseId.get(row.getDispenseId());
                // 缺价行不参与合计，不等于造 0 阻断
                if (pricing == null || pricing.getTotalPrice() == null) {
                    continue;
                }
                grandTotal = grandTotal.add(pricing.getTotalPrice());
            }
        }
        totals.setGrandTotal(grandTotal);
        // lineCount 由 AppService 按当前模式行数回填
        totals.setLineCount(0);
        return totals;
    }

    /**
     * 展示状态 code → 中文文案。
     *
     * @param usageStatusCode 三态 code
     * @return 待发/已发/已退；未知则 null
     */
    private String mapUsageStatusDisplay(String usageStatusCode) {
        // 待发文案
        if (InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_PENDING.equals(usageStatusCode)) {
            return InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_PENDING_DISPLAY;
        }
        // 已发文案
        if (InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_DISPENSED.equals(usageStatusCode)) {
            return InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_DISPENSED_DISPLAY;
        }
        // 已退文案
        if (InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_RETURNED.equals(usageStatusCode)) {
            return InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_RETURNED_DISPLAY;
        }
        return null;
    }

    /**
     * 按药品定义主键解析产品展示。
     *
     * @param medicationId 药品定义主键
     * @param productByKey 复合键 Map
     * @return 产品展示；缺失则 null
     */
    private ProductDefinitionDisplayDto resolveProduct(
        Long medicationId,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        // 缺键或空 Map：明细/汇总名称规格留空
        if (medicationId == null || productByKey == null || productByKey.isEmpty()) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
        return productByKey.get(key);
    }

    /**
     * 解析计量单位展示：优先匹配产品单位槽 DictText，否则回落 code 本身。
     *
     * @param unitCode 单位 code
     * @param product  产品展示
     * @return 展示文案；无 code 时 null
     */
    private String resolveUnitDisplay(String unitCode, ProductDefinitionDisplayDto product) {
        // 无单位 code：不展示
        if (StringUtils.isEmpty(unitCode)) {
            return null;
        }
        String normalized = unitCode.trim();
        // 无产品定义：回落 code 本身
        if (product == null) {
            return normalized;
        }
        // 匹配包装单位展示
        if (normalized.equalsIgnoreCase(safeTrim(product.getPackageUnitCode()))
            && StringUtils.isNotEmpty(product.getPackageUnitCodeDictText())) {
            return product.getPackageUnitCodeDictText();
        }
        // 匹配拆零单位展示
        if (normalized.equalsIgnoreCase(safeTrim(product.getSplitUnitCode()))
            && StringUtils.isNotEmpty(product.getSplitUnitCodeDictText())) {
            return product.getSplitUnitCodeDictText();
        }
        // 匹配库存单位展示
        if (normalized.equalsIgnoreCase(safeTrim(product.getStockUnitCode()))
            && StringUtils.isNotEmpty(product.getStockUnitCodeDictText())) {
            return product.getStockUnitCodeDictText();
        }
        // 匹配剂量单位展示
        if (normalized.equalsIgnoreCase(safeTrim(product.getDoseUnitCode()))
            && StringUtils.isNotEmpty(product.getDoseUnitCodeDictText())) {
            return product.getDoseUnitCodeDictText();
        }
        return normalized;
    }

    /**
     * 安全 trim；null 返回空串便于 equalsIgnoreCase。
     *
     * @param value 原始字符串
     * @return trim 后；null → ""
     */
    private static String safeTrim(String value) {
        // null 与空串同等对待，避免 NPE
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
