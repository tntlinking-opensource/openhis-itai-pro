/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import java.time.LocalDateTime;

/**
 * 住院在院时间窗校验（入院 ≤ 事件 ≤ 出院；起止有序）。
 *
 * <p>纯函数；缺边界字段时跳过对应侧，避免拖垮未出院/未填时间的主路径。
 * 供手术/麻醉开立、病案首页手术行、护士站执行等复用。</p>
 */
public final class EncounterStayTimeBoundsUtil {

    /**
     * 工具类禁止实例化。
     */
    private EncounterStayTimeBoundsUtil() {
    }

    /**
     * 事件时刻相对入院/出院：不得早于入院、不得晚于出院（有界才校）。
     *
     * @param eventTime      业务时刻（拟手术/手术/麻醉起止等）；空则跳过
     * @param admissionTime  入院时间；空则不下界
     * @param dischargeTime  出院时间；空则不上界
     * @param fieldLabel     字段中文名（拼入提示）
     * @return 违规文案；通过或缺字段时返回 {@code null}
     */
    public static String messageIfOutsideStay(
            LocalDateTime eventTime,
            LocalDateTime admissionTime,
            LocalDateTime dischargeTime,
            String fieldLabel) {
        // 未填业务时刻则不拦保存主路径
        if (eventTime == null) {
            return null;
        }
        String label = fieldLabel;
        // 空标签回落通用名，避免拼接出「null不能早于…」
        if (label == null || label.isBlank()) {
            label = "时间";
        }
        // 有入院时间时：事件不得早于入院
        if (admissionTime != null && eventTime.isBefore(admissionTime)) {
            return label + "不能早于入院时间";
        }
        // 已出院时：事件不得晚于出院
        if (dischargeTime != null && eventTime.isAfter(dischargeTime)) {
            return label + "不能晚于出院时间";
        }
        return null;
    }

    /**
     * 起止两端皆填时：结束不得早于开始。
     *
     * @param startTime  开始；空则跳过
     * @param endTime    结束；空则跳过
     * @param startLabel 开始字段中文名
     * @param endLabel   结束字段中文名
     * @return 违规文案；通过或缺一端时返回 {@code null}
     */
    public static String messageIfRangeInverted(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String startLabel,
            String endLabel) {
        // 允许只登一侧（与护士站麻醉弹窗 EX-IP-SAE-203 同口径）
        if (startTime == null || endTime == null) {
            return null;
        }
        // 结束早于开始则拒绝
        if (endTime.isBefore(startTime)) {
            String endName = endLabel;
            if (endName == null || endName.isBlank()) {
                endName = "结束时间";
            }
            String startName = startLabel;
            if (startName == null || startName.isBlank()) {
                startName = "开始时间";
            }
            return endName + "不能早于" + startName;
        }
        return null;
    }
}
