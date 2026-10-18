/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 住院天数计算器（即进不计出）。
 *
 * <p>规则：入院日计、出院日不计。例：11 日入院、13 日出院 → 2 天（11、12）。
 * 同日入出按 1 天。在院未出院时含当日（今日仍算住院日）。</p>
 */
public final class InpatientStayDaysCalculator {

    /**
     * 工具类禁止实例化。
     */
    private InpatientStayDaysCalculator() {
    }

    /**
     * 已出院住院天数：{@code ChronoUnit.DAYS.between(adm, dscg)}，同日至少 1。
     *
     * @param admTime  入院时间；空则无法计算
     * @param dscgTime 出院时间（业务真源：出院医嘱计划时间 / Encounter.endTime）；空则无法计算
     * @return 天数；任一时间为空返回 null
     */
    public static Integer calcDischargedStayDays(LocalDateTime admTime, LocalDateTime dscgTime) {
        if (admTime == null || dscgTime == null) {
            return null;
        }
        return calcDischargedStayDays(admTime.toLocalDate(), dscgTime.toLocalDate());
    }

    /**
     * 已出院住院天数（按自然日）。
     *
     * @param admDate  入院日
     * @param dscgDate 出院日
     * @return 天数；同日为 1
     */
    public static int calcDischargedStayDays(LocalDate admDate, LocalDate dscgDate) {
        long days = ChronoUnit.DAYS.between(admDate, dscgDate);
        // 同日入出或时间序异常：至少 1 天
        if (days < 1L) {
            return 1;
        }
        return (int) days;
    }

    /**
     * 列表/报表展示用住院天数：有出院日走即进不计出；在院含当日。
     *
     * @param admTime  入院时间；空则无法计算
     * @param dscgTime 出院时间；空表示仍在院，截至今天且含当日
     * @return 天数；无入院时间返回 null
     */
    public static Integer calcDisplayStayDays(LocalDateTime admTime, LocalDateTime dscgTime) {
        if (admTime == null) {
            return null;
        }
        LocalDate admDate = admTime.toLocalDate();
        // 已出院：不计出院日
        if (dscgTime != null) {
            return calcDischargedStayDays(admDate, dscgTime.toLocalDate());
        }
        // 在院：含当日（今日仍住院）
        long days = ChronoUnit.DAYS.between(admDate, LocalDate.now()) + 1L;
        if (days < 1L) {
            return 1;
        }
        return (int) days;
    }
}
