/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 药品汇总明细数量展示：拆零持久量 → 满包整数 + 不足一包拆零余量（BR-IP-SUM-015 同族）。
 *
 * <p>供护士站汇总详情（IF-IP-SUM-03）与药房汇总核发合并行（IF-IPS-02）共用。</p>
 */
public final class MedicationPackageSplitQuantityDisplayAssembler {

    /** 工具类禁止实例化。 */
    private MedicationPackageSplitQuantityDisplayAssembler() {
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
        return buildPackageStockDisplayText(
            splitQuantity,
            packageRatio,
            packageUnitText,
            splitUnitText);
    }

    /**
     * 拆零持久量 → 满包整数 + 不足一包拆零余量（BR-IP-SUM-015 / 库存明细 BR-INV-DTL 同族）。
     *
     * @param splitQuantity    拆零最小单位账面数量
     * @param packageRatio     1 包装 = ratio 拆零
     * @param packageUnitLabel 包装单位展示名
     * @param splitUnitLabel   拆零单位展示名
     * @return 展示文案（如 {@code 9盒4片}）；数量为 null 时 null
     */
    public static String buildPackageStockDisplayText(
        BigDecimal splitQuantity,
        BigDecimal packageRatio,
        String packageUnitLabel,
        String splitUnitLabel) {
        if (splitQuantity == null) {
            return null;
        }
        BigDecimal effectiveRatio = packageRatio;
        if (effectiveRatio == null || effectiveRatio.compareTo(BigDecimal.ZERO) <= 0) {
            effectiveRatio = BigDecimal.ONE;
        }
        // 满包整数向下取整，余量用拆零单位拼接
        int packageQty = splitQuantity.divide(effectiveRatio, 0, RoundingMode.DOWN).intValue();
        BigDecimal usedInPackages = effectiveRatio.multiply(BigDecimal.valueOf(packageQty));
        BigDecimal remainder = splitQuantity.subtract(usedInPackages);
        StringBuilder packageText = new StringBuilder();
        if (packageQty > 0) {
            packageText.append(packageQty);
            if (StringUtils.isNotEmpty(packageUnitLabel)) {
                packageText.append(packageUnitLabel);
            }
        } else if (remainder.compareTo(BigDecimal.ZERO) <= 0) {
            packageText.append(GeneralSharedConstants.ZERO);
            if (StringUtils.isNotEmpty(packageUnitLabel)) {
                packageText.append(packageUnitLabel);
            }
        }
        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            packageText.append(remainder.stripTrailingZeros().toPlainString());
            if (StringUtils.isNotEmpty(splitUnitLabel)) {
                packageText.append(splitUnitLabel);
            }
        }
        return packageText.toString();
    }
}
