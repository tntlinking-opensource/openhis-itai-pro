/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.assembler;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.medication.medicationdispense.queries.model.InpatientPatientMedicationUsageDispenseRow;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 住院患者药品使用汇总数量换算（AS-IP-PHA-005 · BR-IP-PMU-007）。
 *
 * <p>同族护士站费用明细：组内数量换算到拆零后轧差，再拆为满包整数 + 不足一包小单位；退药参与冲减；
 * 换算链 6 位 HALF_UP。</p>
 */
@Component
public class InpatientPatientMedicationUsageQuantityAssembler {

    /**
     * 汇总净数量结果：满包整数 + 可选拆零余量。
     *
     * @param packageQuantity   满包装净整数数量（可为负，表示净退）
     * @param splitUnitQuantity 不足一包净小单位数量；余量为 0 时 null
     */
    public record PackageSplitQuantityResult(Integer packageQuantity, BigDecimal splitUnitQuantity) {
    }

    /**
     * 按同药品分组行轧差包装/拆零净数量。
     *
     * @param groupRows 同一 medicationId 的发放行
     * @param product   产品展示读模型（包装比来源）
     * @return 满包与拆零净量；groupRows 空时 packageQuantity=0
     */
    public PackageSplitQuantityResult aggregateNetPackageAndSplitQuantity(
        List<InpatientPatientMedicationUsageDispenseRow> groupRows,
        ProductDefinitionDisplayDto product) {
        // 空组：净数量为 0
        if (groupRows == null || groupRows.isEmpty()) {
            return new PackageSplitQuantityResult(0, null);
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
        BigDecimal totalSplitUnits = BigDecimal.ZERO;
        int scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE;
        // 各行先换算到拆零单位再轧差（退药冲减）
        for (InpatientPatientMedicationUsageDispenseRow row : groupRows) {
            // 无数量无法换算
            if (row == null || row.getQuantityValue() == null) {
                continue;
            }
            BigDecimal quantityValue = row.getQuantityValue();
            // 退药行冲减净用量：正数取负；已为负则保持
            if (row.getDispenseEnum() == MedicationDispenseType.REFUND
                && quantityValue.compareTo(BigDecimal.ZERO) > 0) {
                quantityValue = quantityValue.negate();
            }
            BigDecimal ratioToSplit = this.resolveRatioToSplit(bands, row.getQuantityUnitCode(), product);
            BigDecimal splitQty = quantityValue.multiply(ratioToSplit).setScale(scale, RoundingMode.HALF_UP);
            totalSplitUnits = totalSplitUnits.add(splitQty);
        }
        BigDecimal packageRatio = BigDecimal.ONE;
        // 包装比有效时用于拆满包；否则按 1:1
        if (product != null && product.getPackageUnitRatio() != null
            && product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
            packageRatio = product.getPackageUnitRatio();
        }
        // 净用量为负时向下取整方向仍用 divide DOWN，保留符号在整数部分
        int packageQty = totalSplitUnits.divide(packageRatio, 0, RoundingMode.DOWN).intValue();
        BigDecimal usedInPackages = packageRatio.multiply(BigDecimal.valueOf(packageQty));
        BigDecimal remainder = totalSplitUnits.subtract(usedInPackages).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal splitUnitQuantity = null;
        // 余量为 0 不展示拆零；非零（含负余量）透出
        if (remainder.compareTo(BigDecimal.ZERO) != 0) {
            splitUnitQuantity = remainder;
        }
        return new PackageSplitQuantityResult(packageQty, splitUnitQuantity);
    }

    /**
     * 解析数量单位相对拆零单位的倍数；未知单位回落包装锚或 1。
     *
     * @param bands              产品单位槽
     * @param quantityUnitCode   发放单位 code
     * @param product            产品读模型
     * @return 相对拆零倍数，永不为 null
     */
    private BigDecimal resolveRatioToSplit(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String quantityUnitCode,
        ProductDefinitionDisplayDto product) {
        // 优先命中产品单位槽上的 ratioToSplit
        if (StringUtils.isNotEmpty(quantityUnitCode) && bands != null) {
            String normalized = quantityUnitCode.trim();
            for (ProductDefinitionCatalogUnitBandDto band : bands) {
                // 空槽跳过
                if (band == null || StringUtils.isEmpty(band.getUnitCode())) {
                    continue;
                }
                // 单位码匹配且倍数有效
                if (normalized.equalsIgnoreCase(band.getUnitCode().trim()) && band.getRatioToSplit() != null
                    && band.getRatioToSplit().compareTo(BigDecimal.ZERO) > 0) {
                    return band.getRatioToSplit();
                }
            }
        }
        // 回落包装比；仍无则按 1（数量已在拆零口径）
        if (product != null && product.getPackageUnitRatio() != null
            && product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
            return product.getPackageUnitRatio();
        }
        return BigDecimal.ONE;
    }
}
