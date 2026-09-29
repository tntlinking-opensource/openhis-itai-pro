/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 长期药品日剂量展示：单次拆零量 × 每日次数 →「每日三片」；
 * 有分次 schedule 时改为各段之和（禁止再单值×次数；BR-IP-ORD-037 / BR-IP-EXE-040）。
 *
 * <p>供住院医生站 / 护士站医嘱列表剂量位<strong>友好回显</strong>；仅日周期且每日次数可解析时产出（schedule 路径例外）。</p>
 *
 * <p><strong>边界（BR-IP-ORD-037⑧ 非目标）</strong>：本类<strong>只拼展示文案</strong>，禁止被出院办理待执行判定、
 * 不执行/取消执行写路径、长嘱改量停旧建草稿、停嘱/取消停嘱、出院校验门禁引用为算量或 Gate 依据。
 * schedule 为空时须与「单次×次数」旧口径一致（零侵入）。</p>
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
     * 有分次序列时按各段求和装配日剂量；无序列时回落单次×每日次数。
     *
     * <p>优先 {@code quantitySchedule}（拆零日总量，如「每日四片」）；否则 {@code doseSchedule}
     * （计量日总量）；皆空则走单值×次数。禁止有 schedule 仍用首项×次数（BR-IP-ORD-037⑨）。</p>
     *
     * @param quantitySchedule 拆零逗号串；可空
     * @param quantityUnitName 拆零单位展示名
     * @param doseSchedule     计量逗号串；可空
     * @param doseUnitName     计量单位展示名
     * @param perDoseQty       单次量（无 schedule 时）
     * @param perDoseUnitName  单次单位展示名
     * @param timesPerDay      每日次数（无 schedule 时）
     * @return 日剂量文案；无法装配时 {@code null}
     */
    public static String buildDailyDoseDisplayPreferringSchedule(
        String quantitySchedule,
        String quantityUnitName,
        String doseSchedule,
        String doseUnitName,
        BigDecimal perDoseQty,
        String perDoseUnitName,
        Integer timesPerDay
    ) {
        // 拆零序列：日总量 = 各段之和（与执行侧 quantity_schedule 累加同口径）
        if (StringUtils.isNotEmpty(quantitySchedule)) {
            BigDecimal sum = sumPositiveScheduleSegments(quantitySchedule);
            if (sum != null) {
                return buildDailyDoseDisplay(sum, quantityUnitName, 1);
            }
        }
        // 计量序列：同上，单位走剂量单位
        if (StringUtils.isNotEmpty(doseSchedule)) {
            BigDecimal sum = sumPositiveScheduleSegments(doseSchedule);
            if (sum != null) {
                return buildDailyDoseDisplay(sum, doseUnitName, 1);
            }
        }
        return buildDailyDoseDisplay(perDoseQty, perDoseUnitName, timesPerDay);
    }

    /**
     * 将 schedule 各正数段求和（非法/非正段跳过）。
     *
     * @param schedule 逗号分隔数量串
     * @return 合计；无有效段时 {@code null}
     */
    public static BigDecimal sumPositiveScheduleSegments(String schedule) {
        if (StringUtils.isEmpty(schedule)) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        String[] parts = schedule.split(",");
        for (String part : parts) {
            String trimmed = StringUtils.trimToEmpty(part);
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                BigDecimal value = new BigDecimal(trimmed);
                // 非正段跳过，避免污染日总量
                if (value.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                sum = sum.add(value);
                any = true;
            } catch (NumberFormatException ex) {
                // 跳过非法段，不整串失败
            }
        }
        if (!any) {
            return null;
        }
        return sum;
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
