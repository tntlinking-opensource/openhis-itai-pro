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

package com.openhis.health.module.common.assembler.product;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 药品汇总明细数量展示：拆零持久量 → 满包整数 + 不足一包拆零余量（BR-IP-SUM-015 同族）。
 *
 * <p>供护士站汇总详情（IF-IP-SUM-03）与药房汇总核发合并行（IF-IPS-02）共用。</p>
 */
public final class MedicationPackageSplitQuantityDisplaySupport {

    private MedicationPackageSplitQuantityDisplaySupport() {
    }

    /**
     * 将供应行拆零汇总量格式化为界面数量文案。
     *
     * @param splitQuantity 拆零持久数量
     * @param product       药品目录读模型（包装倍数与单位来源）
     * @return 展示文案（如 {@code 1盒1片}）；无法换算时返回 {@code null}
     */
    public static String buildDisplayQuantityText(
        BigDecimal splitQuantity,
        ProductDefinitionDisplayDto product) {
        if (splitQuantity == null || product == null) {
            return null;
        }
        if (splitQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal packageRatio = product.getPackageUnitRatio();
        if (packageRatio == null || packageRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String packageUnitCode = trimToNull(product.getPackageUnitCode());
        if (packageUnitCode == null) {
            return null;
        }
        String packageUnitText = StringUtils.firstNonBlank(
            product.getPackageUnitCodeDictText(),
            ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(null, packageUnitCode, product));
        String splitUnitText = StringUtils.firstNonBlank(
            product.getSplitUnitCodeDictText(),
            ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                null, product.getSplitUnitCode(), product),
            trimToNull(product.getSplitUnitCode()));
        // 满包整数向下取整，余量用拆零单位拼接
        int packageQty = splitQuantity.divide(packageRatio, 0, RoundingMode.DOWN).intValue();
        BigDecimal usedInPackages = packageRatio.multiply(BigDecimal.valueOf(packageQty));
        BigDecimal remainder = splitQuantity.subtract(usedInPackages);
        StringBuilder quantityText = new StringBuilder();
        if (packageQty > 0) {
            quantityText.append(packageQty);
            if (StringUtils.isNotBlank(packageUnitText)) {
                quantityText.append(packageUnitText);
            }
        }
        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            quantityText.append(remainder.stripTrailingZeros().toPlainString());
            if (StringUtils.isNotBlank(splitUnitText)) {
                quantityText.append(splitUnitText);
            }
        }
        if (quantityText.isEmpty()) {
            return null;
        }
        return quantityText.toString();
    }
}