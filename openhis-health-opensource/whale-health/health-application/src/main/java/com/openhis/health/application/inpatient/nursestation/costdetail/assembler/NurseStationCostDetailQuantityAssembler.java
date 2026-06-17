/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.costdetail.assembler;

import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 费用汇总数量换算（BR-IP-CDT-011）：各行数量统一换算到包装单位，输出满包整数与拆零余量。
 *
 * <p>换算链 6 位 HALF_UP，包装锚来自 {@link ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands}。</p>
 */
@Component
public class NurseStationCostDetailQuantityAssembler {

    /**
     * 汇总数量换算结果：满包整数 + 可选拆零余量（BR-IP-CDT-011）。
     *
     * @param packageQuantity   满包装整数数量
     * @param splitUnitQuantity 不足一包时小单位数量；满包为 null
     */
    public record PackageSplitQuantityResult(Integer packageQuantity, BigDecimal splitUnitQuantity) {
    }

    /**
     * 将同 product 分组内各行数量换算为包装整数与拆零余量（BR-IP-CDT-011）。
     *
     * @param groupRows 同一父项 product 的候选收费行
     * @param product   产品读模型（包装/拆零比来源）
     * @return 满包整数与拆零余量；groupRows 为空时 packageQuantity=0
     */
    public PackageSplitQuantityResult aggregatePackageAndSplitQuantity(
        List<InpatientNurseStationCostDetailChargeRow> groupRows,
        ProductDefinitionDisplayDto product) {
        if (groupRows == null || groupRows.isEmpty()) {
            return new PackageSplitQuantityResult(0, null);
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
        BigDecimal totalSplitUnits = BigDecimal.ZERO;
        int scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE;
        for (InpatientNurseStationCostDetailChargeRow row : groupRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            BigDecimal quantityValue = charge.getQuantityValue();
            if (quantityValue == null) {
                continue;
            }
            // 各行 quantity 先统一换算到拆零单位再累加
            BigDecimal ratioToSplit = this.resolveRatioToSplit(bands, charge.getQuantityUnit(), product);
            BigDecimal splitQty = quantityValue.multiply(ratioToSplit).setScale(scale, RoundingMode.HALF_UP);
            totalSplitUnits = totalSplitUnits.add(splitQty);
        }
        BigDecimal packageRatio = BigDecimal.ONE;
        if (product != null && product.getPackageUnitRatio() != null
            && product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
            packageRatio = product.getPackageUnitRatio();
        }
        int packageQty = totalSplitUnits.divide(packageRatio, 0, RoundingMode.DOWN).intValue();
        BigDecimal usedInPackages = packageRatio.multiply(BigDecimal.valueOf(packageQty));
        BigDecimal remainder = totalSplitUnits.subtract(usedInPackages).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal splitUnitQuantity = null;
        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            splitUnitQuantity = remainder;
        }
        return new PackageSplitQuantityResult(packageQty, splitUnitQuantity);
    }

    /**
     * 解析账单数量单位相对拆零单位的倍数；未知单位回落包装锚或 1。
     *
     * @param bands        产品单位槽
     * @param quantityUnit 账单单位 code
     * @param product      产品读模型
     * @return 相对拆零倍数，永不为 null
     */
    private BigDecimal resolveRatioToSplit(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String quantityUnit,
        ProductDefinitionDisplayDto product) {
        if (StringUtils.isNotBlank(quantityUnit) && bands != null) {
            String normalized = quantityUnit.trim();
            for (ProductDefinitionCatalogUnitBandDto band : bands) {
                if (band == null || StringUtils.isBlank(band.getUnitCode())) {
                    continue;
                }
                if (normalized.equalsIgnoreCase(band.getUnitCode().trim()) && band.getRatioToSplit() != null
                    && band.getRatioToSplit().compareTo(BigDecimal.ZERO) > 0) {
                    return band.getRatioToSplit();
                }
            }
        }
        if (product != null && product.getPackageUnitRatio() != null
            && product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
            return product.getPackageUnitRatio();
        }
        return BigDecimal.ONE;
    }
}
