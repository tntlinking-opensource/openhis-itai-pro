/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.product;

import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * 药品单次剂量展示文案装配：最小（拆零）单位为主，必要时括号内附计量单位。
 *
 * <p>口径与医生站开立双单位一致：{@code splitDose = dose / doseUnitRatio}
 * （{@code doseUnitRatio} 表示「1 最小单位对应多少计量单位」，与开立侧 {@code doseBandConvertK} 同源）。
 * 计量单位与最小单位相同（比 code，缺则比展示名）时不拼括号。</p>
 *
 * <p><strong>输液例外</strong>：目录 {@code injectFlag=true} 时一律主显计量单位
 * （如 {@code 250ml/次}、{@code 10ml/次}），禁止列表出现「一次1瓶 / 0.1瓶」；
 * 结构化拆零字段仍下发供编辑回填。</p>
 *
 * <p>小数位：计量（剂量单位）展示固定 3 位（主显计量时去尾零）；最小单位展示 2 位（与开立页换算展示对齐）。
 * 计量单位字典名「毫升」在展示串中归一为 {@code ml}。</p>
 *
 * <p>示例：{@code 0.75片(35.625毫克)/次}；单位相同时 {@code 1片/次}；输液 {@code 250ml/次}。</p>
 */
public final class MedicationDosageDisplayAssembler {

    /** 计量（剂量单位）展示小数位。 */
    private static final int DOSE_DISPLAY_SCALE = 3;

    /** 最小单位（拆零）展示小数位。 */
    private static final int SPLIT_DISPLAY_SCALE = 2;

    /** 工具类禁止实例化。 */
    private MedicationDosageDisplayAssembler() {
    }

    /**
     * 由单次剂量与产品单位槽装配展示结果。
     *
     * @param dose                 单次剂量（计量/DOSE 带数量）；null 时返回空结果
     * @param doseUnitCode         剂量单位编码
     * @param doseUnitLabelByCode  剂量单位术语映射；可为 null
     * @param productRow           产品读模型（拆零单位与换算比）；可为 null
     * @return 展示结果；dose 为空时 dosageDisplay 为 null
     */
    public static MedicationDosageDisplayResult build(
        BigDecimal dose,
        String doseUnitCode,
        Map<String, String> doseUnitLabelByCode,
        ProductDefinitionDisplayDto productRow
    ) {
        if (dose == null) {
            return MedicationDosageDisplayResult.empty();
        }
        String doseText = formatDoseQuantity(dose);
        String doseUnitName = ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
            doseUnitLabelByCode, doseUnitCode, productRow);
        // 展示串单位：毫升 → ml（结构化 doseUnitName 仍保留字典原名供编辑）
        String doseUnitDisplay = normalizeDoseDisplayUnit(doseUnitName);
        String dosePart = doseText;
        if (StringUtils.isNotEmpty(doseUnitDisplay)) {
            dosePart = doseText + doseUnitDisplay;
        }

        if (productRow == null) {
            return MedicationDosageDisplayResult.ofDisplayOnly(appendPerDoseSuffix(dosePart));
        }

        String splitUnitCode = StringUtils.trimToNull(productRow.getSplitUnitCode());
        String splitUnitName = resolveSplitUnitDisplayName(productRow, splitUnitCode);
        // 计量单位与最小单位一致：只回最小/计量同义段+/次，不拼括号
        if (isSameUnit(doseUnitCode, doseUnitName, splitUnitCode, splitUnitName)) {
            return MedicationDosageDisplayResult.of(
                appendPerDoseSuffix(dosePart),
                null,
                splitUnitCode,
                splitUnitName,
                false);
        }

        BigDecimal ratio = productRow.getDoseUnitRatio();
        // 换算比缺失或非正：无法算最小单位量，仅回计量段+/次
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return MedicationDosageDisplayResult.ofDisplayOnly(appendPerDoseSuffix(dosePart));
        }
        if (StringUtils.isEmpty(splitUnitName) && StringUtils.isEmpty(splitUnitCode)) {
            return MedicationDosageDisplayResult.ofDisplayOnly(appendPerDoseSuffix(dosePart));
        }

        // 开立口径：拆零量 = 剂量 ÷ doseUnitRatio（1 最小单位对应 ratio 个计量单位）
        BigDecimal splitDose = dose.divide(ratio, SPLIT_DISPLAY_SCALE, RoundingMode.HALF_UP);
        // 输液/注射：主显计量单位（一次250ml，禁止一次1瓶）
        if (shouldPreferDoseUnitForInfusion(productRow)) {
            String friendlyDosePart = formatFriendlyDosePart(dose, doseUnitDisplay);
            return MedicationDosageDisplayResult.of(
                appendPerDoseSuffix(friendlyDosePart),
                splitDose,
                splitUnitCode,
                splitUnitName,
                true);
        }
        String splitText = formatSplitQuantity(splitDose);
        String splitLabel = normalizeDoseDisplayUnit(splitUnitName);
        if (StringUtils.isEmpty(splitLabel)) {
            splitLabel = splitUnitCode;
        }
        // 主显最小单位，括号内为计量单位：0.75片(35.625毫克)/次
        String dosageDisplay = appendPerDoseSuffix(splitText + splitLabel + "(" + dosePart + ")");
        return MedicationDosageDisplayResult.of(
            dosageDisplay,
            splitDose,
            splitUnitCode,
            splitUnitName,
            false);
    }

    /**
     * 剂量展示串末尾补「/次」（已有则不重复）。
     *
     * @param doseCore 计量段（可含最小单位括号）
     * @return 带 /次 的展示串
     */
    private static String appendPerDoseSuffix(String doseCore) {
        if (StringUtils.isEmpty(doseCore)) {
            return doseCore;
        }
        if (doseCore.endsWith("/次")) {
            return doseCore;
        }
        return doseCore + "/次";
    }

    /**
     * 仅需要展示串时的便捷方法（无拆零字段时用）。
     *
     * @param dose                单次剂量
     * @param doseUnitCode        剂量单位编码
     * @param doseUnitLabelByCode 术语映射
     * @param productRow          产品读模型
     * @return 展示串；dose 为空时 null
     */
    public static String formatDosageDisplay(
        BigDecimal dose,
        String doseUnitCode,
        Map<String, String> doseUnitLabelByCode,
        ProductDefinitionDisplayDto productRow
    ) {
        return build(dose, doseUnitCode, doseUnitLabelByCode, productRow).getDosageDisplay();
    }

    /**
     * 计量（剂量单位）数量展示：固定 3 位小数（括号附计量时用）。
     *
     * @param qty 数量
     * @return 纯数字串
     */
    private static String formatDoseQuantity(BigDecimal qty) {
        return qty.setScale(DOSE_DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 主显计量单位时的数量+单位段：3 位半入后去尾零（如 10.000→10ml）。
     *
     * @param dose         单次计量数量
     * @param doseUnitName 计量单位展示名（已归一或原名字典）；可空
     * @return 如 {@code 10ml}；单位空时仅数字
     */
    private static String formatFriendlyDosePart(BigDecimal dose, String doseUnitName) {
        String qtyText = dose.setScale(DOSE_DISPLAY_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
        String unit = normalizeDoseDisplayUnit(doseUnitName);
        if (StringUtils.isNotEmpty(unit)) {
            return qtyText + unit;
        }
        return qtyText;
    }

    /**
     * 剂量展示单位归一：字典「毫升」与医嘱内容规格统一为 {@code ml}。
     *
     * @param unitName 单位展示名；可空
     * @return 归一后单位；入参空则原样（可空）
     */
    private static String normalizeDoseDisplayUnit(String unitName) {
        if (StringUtils.isEmpty(unitName)) {
            return unitName;
        }
        String trimmed = unitName.trim();
        if ("毫升".equals(trimmed)) {
            return "ml";
        }
        return trimmed;
    }

    /**
     * 最小单位数量展示：2 位小数并去尾零（如 1.00→1、0.75→0.75）。
     *
     * @param qty 数量
     * @return 纯数字串
     */
    private static String formatSplitQuantity(BigDecimal qty) {
        return qty.setScale(SPLIT_DISPLAY_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }

    /**
     * 输液/注射目录：列表主显一律改用计量单位（如 ml/g），禁止瓶/袋拆零主显。
     *
     * @param productRow 产品读模型
     * @return true 时 dosageDisplay / 日剂量友好文案走计量侧
     */
    private static boolean shouldPreferDoseUnitForInfusion(ProductDefinitionDisplayDto productRow) {
        if (productRow == null) {
            return false;
        }
        // 非输液/注射目录保持「最小单位为主」
        return Boolean.TRUE.equals(productRow.getInjectFlag());
    }

    /**
     * 解析拆零单位展示名：产品字典列优先，再回落单位槽解析。
     *
     * @param productRow    产品读模型
     * @param splitUnitCode 拆零单位编码
     * @return 展示名；未命中可为 null
     */
    private static String resolveSplitUnitDisplayName(
        ProductDefinitionDisplayDto productRow,
        String splitUnitCode
    ) {
        String fromDict = StringUtils.trimToNull(productRow.getSplitUnitCodeDictText());
        if (fromDict != null) {
            return fromDict;
        }
        return ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            null, splitUnitCode, productRow);
    }

    /**
     * 判定计量单位与最小单位是否同一：优先比 code，两侧 code 皆空时比展示名。
     *
     * @param doseUnitCode  剂量单位编码
     * @param doseUnitName  剂量单位展示名
     * @param splitUnitCode 拆零单位编码
     * @param splitUnitName 拆零单位展示名
     * @return true 视为同单位，不拼括号
     */
    private static boolean isSameUnit(
        String doseUnitCode,
        String doseUnitName,
        String splitUnitCode,
        String splitUnitName
    ) {
        String doseCode = StringUtils.trimToEmpty(doseUnitCode);
        String splitCode = StringUtils.trimToEmpty(splitUnitCode);
        if (StringUtils.isNotEmpty(doseCode) && StringUtils.isNotEmpty(splitCode)) {
            return Objects.equals(doseCode, splitCode);
        }
        String doseName = StringUtils.trimToEmpty(doseUnitName);
        String splitName = StringUtils.trimToEmpty(splitUnitName);
        if (StringUtils.isNotEmpty(doseName) && StringUtils.isNotEmpty(splitName)) {
            return Objects.equals(doseName, splitName);
        }
        return false;
    }

    /**
     * 剂量展示装配结果：展示串 + 可选拆零数量/单位（供列表 DTO 结构化下发）。
     */
    public static final class MedicationDosageDisplayResult {

        /** 完整展示串，如 {@code 1支(2ml)/次}、{@code 1片/次} 或输液 {@code 10ml/次}。 */
        private final String dosageDisplay;

        /** 单次剂量对应的最小单位数量；同单位或无法换算时为 null。 */
        private final BigDecimal splitDose;

        /** 最小单位编码。 */
        private final String splitDoseUnitCode;

        /** 最小单位展示名。 */
        private final String splitDoseUnitName;

        /**
         * 是否因「输液/注射目录」主显计量单位（日剂量拼装须同走计量侧）。
         */
        private final boolean preferDoseUnitDisplay;

        private MedicationDosageDisplayResult(
            String dosageDisplay,
            BigDecimal splitDose,
            String splitDoseUnitCode,
            String splitDoseUnitName,
            boolean preferDoseUnitDisplay
        ) {
            this.dosageDisplay = dosageDisplay;
            this.splitDose = splitDose;
            this.splitDoseUnitCode = splitDoseUnitCode;
            this.splitDoseUnitName = splitDoseUnitName;
            this.preferDoseUnitDisplay = preferDoseUnitDisplay;
        }

        /** 空结果（无剂量）。 */
        public static MedicationDosageDisplayResult empty() {
            return new MedicationDosageDisplayResult(null, null, null, null, false);
        }

        /**
         * 仅有展示串、无拆零结构化字段。
         *
         * @param dosageDisplay 展示串
         * @return 结果
         */
        public static MedicationDosageDisplayResult ofDisplayOnly(String dosageDisplay) {
            return new MedicationDosageDisplayResult(dosageDisplay, null, null, null, false);
        }

        /**
         * 完整结果（默认非输液计量主显）。
         *
         * @param dosageDisplay     展示串
         * @param splitDose         拆零量
         * @param splitDoseUnitCode 拆零单位码
         * @param splitDoseUnitName 拆零单位名
         * @return 结果
         */
        public static MedicationDosageDisplayResult of(
            String dosageDisplay,
            BigDecimal splitDose,
            String splitDoseUnitCode,
            String splitDoseUnitName
        ) {
            return of(dosageDisplay, splitDose, splitDoseUnitCode, splitDoseUnitName, false);
        }

        /**
         * 完整结果（可标记输液计量主显）。
         *
         * @param dosageDisplay          展示串
         * @param splitDose              拆零量
         * @param splitDoseUnitCode      拆零单位码
         * @param splitDoseUnitName      拆零单位名
         * @param preferDoseUnitDisplay  true=日剂量须跟计量侧
         * @return 结果
         */
        public static MedicationDosageDisplayResult of(
            String dosageDisplay,
            BigDecimal splitDose,
            String splitDoseUnitCode,
            String splitDoseUnitName,
            boolean preferDoseUnitDisplay
        ) {
            return new MedicationDosageDisplayResult(
                dosageDisplay,
                splitDose,
                splitDoseUnitCode,
                splitDoseUnitName,
                preferDoseUnitDisplay);
        }

        public String getDosageDisplay() {
            return dosageDisplay;
        }

        public BigDecimal getSplitDose() {
            return splitDose;
        }

        public String getSplitDoseUnitCode() {
            return splitDoseUnitCode;
        }

        public String getSplitDoseUnitName() {
            return splitDoseUnitName;
        }

        /**
         * @return true 时列表/日剂量友好文案应使用计量侧数量与单位
         */
        public boolean isPreferDoseUnitDisplay() {
            return preferDoseUnitDisplay;
        }

        /**
         * 日剂量拼装用的单次数量：输液计量主显时回计量 dose，否则优先拆零量。
         *
         * @param dose 单次计量数量（回落）
         * @return 单次量；皆空时 null
         */
        public BigDecimal resolveFriendlyPerDoseQty(BigDecimal dose) {
            // 输液/注射：日剂量与 dosageDisplay 同走毫升等计量单位
            if (preferDoseUnitDisplay) {
                return dose;
            }
            if (splitDose != null) {
                return splitDose;
            }
            return dose;
        }

        /**
         * 日剂量拼装用的单次单位名：与 {@link #resolveFriendlyPerDoseQty} 成对。
         *
         * @param doseUnitName 计量单位展示名
         * @return 单位展示名；可空
         */
        public String resolveFriendlyPerDoseUnitName(String doseUnitName) {
            // 日剂量展示串单位：毫升 → ml（与 dosageDisplay 同口径）
            if (preferDoseUnitDisplay) {
                return normalizeDoseDisplayUnit(doseUnitName);
            }
            if (StringUtils.isNotEmpty(splitDoseUnitName)) {
                return normalizeDoseDisplayUnit(splitDoseUnitName);
            }
            return normalizeDoseDisplayUnit(doseUnitName);
        }
    }
}
