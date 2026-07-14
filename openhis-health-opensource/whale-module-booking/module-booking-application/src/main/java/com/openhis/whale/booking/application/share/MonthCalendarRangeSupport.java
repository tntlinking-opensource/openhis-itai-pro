/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.share;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 月历可见区间计算（周一为一周起点，对齐周历）。
 */
public final class MonthCalendarRangeSupport {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private MonthCalendarRangeSupport() {
    }

    /**
     * 解析 yyyy-MM 为自然月区间。
     *
     * @param yearMonth 自然月字符串
     * @return [monthStart, monthEnd]
     */
    public static LocalDate[] resolveMonthRange(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "yearMonth 不能为空");
        }
        try {
            YearMonth ym = YearMonth.parse(yearMonth.trim(), YEAR_MONTH);
            return new LocalDate[] { ym.atDay(1), ym.atEndOfMonth() };
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "yearMonth 格式须为 yyyy-MM");
        }
    }

    /**
     * 计算月历网格区间：从 monthStart 所在周一起，到 monthEnd 所在周日止。
     *
     * @param monthStart 自然月第一天
     * @param monthEnd 自然月最后一天
     * @return [gridStart, gridEnd]
     */
    public static LocalDate[] resolveGridRange(LocalDate monthStart, LocalDate monthEnd) {
        LocalDate gridStart = shiftToMonday(monthStart);
        LocalDate gridEnd = shiftToSunday(monthEnd);
        return new LocalDate[] { gridStart, gridEnd };
    }

    private static LocalDate shiftToMonday(LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        return date.minusDays(dayOfWeek - 1L);
    }

    private static LocalDate shiftToSunday(LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        return date.plusDays(7L - dayOfWeek);
    }
}
