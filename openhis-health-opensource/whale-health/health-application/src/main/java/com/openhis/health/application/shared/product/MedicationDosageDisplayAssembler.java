/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;

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
 * <p>示例：{@code 1支(2ml)/次}；单位相同时 {@code 1片/次}。</p>
 */
public final class MedicationDosageDisplayAssembler {

    /** 剂量数量展示小数位（与护士站原 formatDosageDisplay 一致）。 */
    private static final int DOSE_DISPLAY_SCALE = 4;

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
        String doseText = formatPlainQuantity(dose);
        String doseUnitName = ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
            doseUnitLabelByCode, doseUnitCode, productRow);
        String dosePart = doseText;
        if (StringUtils.isNotEmpty(doseUnitName)) {
            dosePart = doseText + doseUnitName;
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
                splitUnitName);
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
        BigDecimal splitDose = dose.divide(ratio, DOSE_DISPLAY_SCALE, RoundingMode.HALF_UP);
        String splitText = formatPlainQuantity(splitDose);
        String splitLabel = splitUnitName;
        if (StringUtils.isEmpty(splitLabel)) {
            splitLabel = splitUnitCode;
        }
        // 主显最小单位，括号内为计量单位：1支(2ml)/次
        String dosageDisplay = appendPerDoseSuffix(splitText + splitLabel + "(" + dosePart + ")");
        return MedicationDosageDisplayResult.of(
            dosageDisplay,
            splitDose.stripTrailingZeros(),
            splitUnitCode,
            splitUnitName);
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
     * 数量去尾零纯文本（避免科学计数）。
     *
     * @param qty 数量
     * @return 纯数字串
     */
    private static String formatPlainQuantity(BigDecimal qty) {
        return qty.setScale(DOSE_DISPLAY_SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
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

        /** 完整展示串，如 {@code 1支(2ml)/次} 或 {@code 1片/次}。 */
        private final String dosageDisplay;

        /** 单次剂量对应的最小单位数量；同单位或无法换算时为 null。 */
        private final BigDecimal splitDose;

        /** 最小单位编码。 */
        private final String splitDoseUnitCode;

        /** 最小单位展示名。 */
        private final String splitDoseUnitName;

        private MedicationDosageDisplayResult(
            String dosageDisplay,
            BigDecimal splitDose,
            String splitDoseUnitCode,
            String splitDoseUnitName
        ) {
            this.dosageDisplay = dosageDisplay;
            this.splitDose = splitDose;
            this.splitDoseUnitCode = splitDoseUnitCode;
            this.splitDoseUnitName = splitDoseUnitName;
        }

        /** 空结果（无剂量）。 */
        public static MedicationDosageDisplayResult empty() {
            return new MedicationDosageDisplayResult(null, null, null, null);
        }

        /**
         * 仅有展示串、无拆零结构化字段。
         *
         * @param dosageDisplay 展示串
         * @return 结果
         */
        public static MedicationDosageDisplayResult ofDisplayOnly(String dosageDisplay) {
            return new MedicationDosageDisplayResult(dosageDisplay, null, null, null);
        }

        /**
         * 完整结果。
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
            return new MedicationDosageDisplayResult(
                dosageDisplay, splitDose, splitDoseUnitCode, splitDoseUnitName);
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
    }
}
