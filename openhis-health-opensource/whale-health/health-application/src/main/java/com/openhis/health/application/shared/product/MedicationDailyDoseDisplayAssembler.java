/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 长期药品日剂量展示：单次拆零量 × 每日次数 →「每日三片」。
 *
 * <p>供住院医生站 / 护士站医嘱列表剂量位友好回显；仅日周期且每日次数可解析时产出。</p>
 */
public final class MedicationDailyDoseDisplayAssembler {

    /** 拆零量展示小数位（与单次剂量最小单位口径对齐）。 */
    private static final int SPLIT_DISPLAY_SCALE = 2;

    /** 1～10 的中文数字（日剂量整数友好展示）。 */
    private static final String[] CHINESE_DIGITS = {
        "零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"
    };

    /** 工具类禁止实例化。 */
    private MedicationDailyDoseDisplayAssembler() {
    }

    /**
     * 装配日剂量展示文案。
     *
     * @param splitDose     单次拆零数量
     * @param splitUnitName 拆零单位展示名（如片）
     * @param timesPerDay   每日执行次数；空或非正时无法装配
     * @return 如 {@code 每日三片}；无法装配时 {@code null}
     */
    public static String buildDailyDoseDisplay(
        BigDecimal splitDose,
        String splitUnitName,
        Integer timesPerDay
    ) {
        if (splitDose == null || timesPerDay == null || timesPerDay <= 0) {
            return null;
        }
        if (splitDose.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String unit = StringUtils.trimToNull(splitUnitName);
        if (unit == null) {
            return null;
        }
        BigDecimal dailyQty = splitDose.multiply(BigDecimal.valueOf(timesPerDay));
        String qtyText = formatDailyQuantity(dailyQty);
        if (StringUtils.isEmpty(qtyText)) {
            return null;
        }
        return "每日" + qtyText + unit;
    }

    /**
     * 日剂量数量文案：正整数 1～10 用中文（三）；其余用去尾零阿拉伯数字。
     *
     * @param dailyQty 日剂量数量
     * @return 数量文案
     */
    private static String formatDailyQuantity(BigDecimal dailyQty) {
        if (dailyQty == null) {
            return null;
        }
        BigDecimal normalized = dailyQty.stripTrailingZeros();
        // 正整数 1～10：每日三片
        if (normalized.scale() <= 0) {
            try {
                int intQty = normalized.intValueExact();
                if (intQty >= 1 && intQty <= 10) {
                    return CHINESE_DIGITS[intQty];
                }
                return String.valueOf(intQty);
            } catch (ArithmeticException ignored) {
                // 超出 int 范围：走小数口径
            }
        }
        return dailyQty.setScale(SPLIT_DISPLAY_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }
}
