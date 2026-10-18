/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.product;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 药品数量展示装配：汇总详情直显操作/行单位；库存与可开量仍可用满包+余量。
 *
 * <p>汇总合并明细（IF-IP-SUM-03 / IF-IPS-02）走 {@link #buildSummaryQuantityText}：
 * <b>输液</b>（{@code injectFlag=true}）一律拆零单位直显；
 * <b>非输液</b>拆零量恰为整包装时按包装单位凑整（如 14 片/盒 → {@code 1盒}），否则拆零直显；
 * 仍禁止「1盒5支」满包余量拼接；金额只读累加不重算（BR-IP-SUM-015）。
 * 摆药（IF-IP-SUM-09）仍用 {@link #buildPlainQuantityText}（操作单位累加）。
 * 库存明细 {@code packageStockText}、医生站可开量等仍用 {@link #buildDisplayQuantityText}/
 * {@link #buildPackageStockDisplayText}。</p>
 */
public final class MedicationPackageSplitQuantityDisplayAssembler {

    /** 工具类禁止实例化。 */
    private MedicationPackageSplitQuantityDisplayAssembler() {
    }

    /**
     * 数量 + 单位直显（禁止满包整数 + 拆零余量拼接）。
     *
     * <p>摆药（IF-IP-SUM-09）等操作单位累加场景共用：跟着行上单位走，如 {@code 15支}；
     * 不改持久数量，不重算 {@code totalPrice}。</p>
     *
     * @param quantity  与单位同阶的数量（Supply 为拆零；摆药为发放操作单位累加）
     * @param unitLabel 单位展示名（与 quantity 同阶）
     * @return 展示文案（如 {@code 15支}）；数量空或非正时 {@code null}
     */
    public static String buildPlainQuantityText(BigDecimal quantity, String unitLabel) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        text.append(quantity.stripTrailingZeros().toPlainString());
        // 有单位名则紧拼接，与历史「数量+单位」无空格口径一致
        if (StringUtils.isNotEmpty(unitLabel)) {
            text.append(unitLabel);
        }
        return text.toString();
    }

    /**
     * 汇总合并明细数量文案（BR-IP-SUM-015）：输液拆零直显；非输液整包装凑整。
     *
     * <p>Supply 持久量为拆零。目录 {@code injectFlag=true}（输液/注射）一律拆零单位直显
     * （如 {@code 6袋}），不按包装凑整。非输液且拆零量可被 {@code packageUnitRatio} 整除、
     * 包装≠拆零时，展示为包装件数（如 14 片且 1 盒=14 片 → {@code 1盒}）。
     * 有余量时仍拆零直显，禁止拼出「1盒5支」。缺目录/包装比时回落拆零单位文案。</p>
     *
     * @param splitQuantity 拆零持久数量（与 Supply.itemQuantity 同阶）
     * @param product       药品目录读模型（包装倍数与单位来源）
     * @param splitUnitLabel 拆零单位展示名（调用方已解析；可空）
     * @return 展示文案；数量空或非正时 {@code null}
     */
    public static String buildSummaryQuantityText(
        BigDecimal splitQuantity,
        ProductDefinitionDisplayDto product,
        String splitUnitLabel) {
        if (splitQuantity == null || splitQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        // Gate：无目录 → 拆零直显
        if (product == null) {
            return buildPlainQuantityText(splitQuantity, splitUnitLabel);
        }
        // Gate：输液/注射目录一律拆零单位，不按包装凑整
        if (Boolean.TRUE.equals(product.getInjectFlag())) {
            return buildPlainQuantityText(splitQuantity, splitUnitLabel);
        }
        BigDecimal packageRatio = product.getPackageUnitRatio();
        String packageUnitCode = trimToNull(product.getPackageUnitCode());
        String splitUnitCode = trimToNull(product.getSplitUnitCode());
        // Gate：包装槽不可用 → 拆零直显
        if (packageRatio == null
            || packageRatio.compareTo(BigDecimal.ONE) <= 0
            || packageUnitCode == null
            || packageUnitCode.equals(splitUnitCode)) {
            return buildPlainQuantityText(splitQuantity, splitUnitLabel);
        }
        // Pre：整除判定——有余量则不升包装，避免满包+余量拼接
        BigDecimal[] divAndRem = splitQuantity.divideAndRemainder(packageRatio);
        BigDecimal packageQty = divAndRem[0];
        BigDecimal remainder = divAndRem[1];
        if (remainder.compareTo(BigDecimal.ZERO) != 0
            || packageQty.compareTo(BigDecimal.ZERO) <= 0) {
            return buildPlainQuantityText(splitQuantity, splitUnitLabel);
        }
        // Asm：非输液且恰为整包装 → 包装件数 + 包装单位
        String packageUnitText = StringUtils.firstNonBlank(
            product.getPackageUnitCodeDictText(),
            ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                null, packageUnitCode, product),
            packageUnitCode);
        return buildPlainQuantityText(packageQty, packageUnitText);
    }

    /**
     * 将拆零汇总量格式化为满包整数 + 拆零余量（库存/可开量展示；汇总详情勿用）。
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
     * 包装与拆零同单位且倍数为 1 时无信息量，返回 null。
     * 输液/注射目录（{@code injectFlag=true}）不拼装量后缀（如 {@code 1瓶x40}），仅保留规格。</p>
     *
     * @param product 药品目录读模型（包装倍数与拆零单位来源）
     * @return 装量后缀；缺倍数或拆零单位或输液目录时 {@code null}
     */
    public static String buildPackageSplitRatioText(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        // 输液/注射：医嘱内容只保留规格（【250ml: 50g】），不拼「一件多少瓶/袋」
        if (Boolean.TRUE.equals(product.getInjectFlag())) {
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

    /**
     * 从产品读模型拼装库房口径「包装↔拆零」比例文案（发药单规格旁、库存明细同源）。
     *
     * <p>单位展示名优先读模型字典文本，缺省回落 {@link ProductDefinitionCatalogUnitBandsAssembler}
     * 解析名或单位码；禁止输出临床 {@code 1片x100} 装量后缀。</p>
     *
     * @param product 药品/耗材目录读模型
     * @return 展示文案（如 {@code 1盒/36片}）；缺倍数或产品时 {@code null}
     */
    public static String buildInventoryPackageSplitRatioText(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        String packageUnitCode = trimToNull(product.getPackageUnitCode());
        String splitUnitCode = trimToNull(product.getSplitUnitCode());
        String packageUnitText = StringUtils.firstNonBlank(
            product.getPackageUnitCodeDictText(),
            ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(null, packageUnitCode, product),
            packageUnitCode);
        String splitUnitText = StringUtils.firstNonBlank(
            product.getSplitUnitCodeDictText(),
            ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(null, splitUnitCode, product),
            splitUnitCode);
        return buildInventoryPackageSplitRatioText(
            product.getPackageUnitRatio(),
            packageUnitText,
            splitUnitText);
    }
}
