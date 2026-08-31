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
 * <p>供护士站汇总详情（IF-IP-SUM-03）、药房汇总核发合并行（IF-IPS-02）、库存明细
 * {@code packageStockText}/{@code packageSplitRatioText}，以及医生站目录可开量 {@code availableQuantityText} 共用。</p>
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

    /**
     * 包装装量后缀：拼入规格括号，形如 {@code 1片x100}（与规格拼接为 {@code 5mg1片x100}）。
     *
     * <p>供住院医生站 / 护士站医嘱列表；禁止再输出「1盒=24片」口语口径。
     * 包装与拆零同单位且倍数为 1 时无信息量，返回 null。</p>
     *
     * @param product 药品目录读模型（包装倍数与拆零单位来源）
     * @return 装量后缀；缺倍数或拆零单位时 {@code null}
     */
    public static String buildPackageSplitRatioText(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        BigDecimal packageRatio = product.getPackageUnitRatio();
        if (packageRatio == null || packageRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String splitUnitCode = trimToNull(product.getSplitUnitCode());
        String packageUnitCode = trimToNull(product.getPackageUnitCode());
        String splitUnitText = StringUtils.firstNonBlank(
            product.getSplitUnitCodeDictText(),
            ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(null, splitUnitCode, product),
            splitUnitCode);
        if (StringUtils.isEmpty(splitUnitText)) {
            return null;
        }
        String packageUnitText = StringUtils.firstNonBlank(
            product.getPackageUnitCodeDictText(),
            ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(null, packageUnitCode, product),
            packageUnitCode);
        // 同单位且倍数为 1：无装量信息，不展示
        if (StringUtils.isNotEmpty(packageUnitText)
            && packageUnitText.equals(splitUnitText)
            && packageRatio.compareTo(BigDecimal.ONE) == 0) {
            return null;
        }
        String ratioText = packageRatio.stripTrailingZeros().toPlainString();
        // 临床规格装量：1片x100（前端与 totalVolume「5mg」拼成 5mg1片x100）
        return GeneralSharedConstants.DIGIT_ONE
            + splitUnitText
            + GeneralSharedConstants.CLINICAL_PACKAGE_SPLIT_RATIO_MARK
            + ratioText;
    }

    /**
     * 库房库存明细「包装拆零比例」展示：形如 {@code 1盒/36片}（1 包装单位 / ratio 拆零单位）。
     *
     * <p>与临床医嘱装量后缀 {@link #buildPackageSplitRatioText}（{@code 1片x100}）口径不同，禁止混用。
     * 包装与拆零同单位且倍数为 1 时仅展示 {@code 1支}（无换算斜杠段）。</p>
     *
     * @param packageUnitRatio 1 包装 = ratio 拆零；须 &gt; 0
     * @param packageUnitLabel 包装单位展示名（如盒）
     * @param splitUnitLabel   拆零单位展示名（如片）
     * @return 展示文案；缺有效倍数时 {@code null}
     */
    public static String buildInventoryPackageSplitRatioText(
        BigDecimal packageUnitRatio,
        String packageUnitLabel,
        String splitUnitLabel) {
        if (packageUnitRatio == null || packageUnitRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String packageLabel = trimToNull(packageUnitLabel);
        String splitLabel = trimToNull(splitUnitLabel);
        // 同单位且倍数为 1：无换算，仅展示 1支
        if (packageLabel != null
            && packageLabel.equals(splitLabel)
            && packageUnitRatio.compareTo(BigDecimal.ONE) == 0) {
            return GeneralSharedConstants.DIGIT_ONE + packageLabel;
        }
        StringBuilder text = new StringBuilder();
        text.append(GeneralSharedConstants.DIGIT_ONE);
        if (packageLabel != null) {
            text.append(packageLabel);
        }
        text.append(GeneralSharedConstants.SLASH);
        text.append(packageUnitRatio.stripTrailingZeros().toPlainString());
        if (splitLabel != null) {
            text.append(splitLabel);
        }
        return text.toString();
    }
}
