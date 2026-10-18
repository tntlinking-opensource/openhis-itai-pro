/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.report;

import com.zxplat.domain.share.constants.GeneralSharedConstants;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * 报表查询时间窗解析（费用明细 / 发药·耗材使用明细等共用）。
 *
 * <p>契约：Request 字段仍为 {@code String dateFrom/dateTo}；允许 {@code yyyy-MM-dd} 或
 * {@code yyyy-MM-dd HH:mm:ss}（亦兼容 {@code T} 分隔与缺秒）。仅日历日时窗起补
 * {@code 00:00:00}、窗止补 {@code 23:59:59}；已含时分秒则原样进入闭区间。
 * 域层 {@code lessThan} 开区间上界用 {@link #toExclusiveEnd(LocalDateTime)}（+1 秒）。</p>
 */
public final class ReportDateTimeWindowSupport {

    private ReportDateTimeWindowSupport() {
    }

    /**
     * 解析查询窗起（闭区间下界）。
     *
     * @param rawText 日起或日起时刻；空则抛 {@link DateTimeParseException}
     * @return 本地时刻
     */
    public static LocalDateTime parseInclusiveStart(String rawText) {
        return parseBound(rawText, true);
    }

    /**
     * 解析查询窗止（闭区间上界）。
     *
     * @param rawText 日止或日止时刻；空则抛 {@link DateTimeParseException}
     * @return 本地时刻
     */
    public static LocalDateTime parseInclusiveEnd(String rawText) {
        return parseBound(rawText, false);
    }

    /**
     * 闭区间上界 → 开区间上界（+1 秒），供 JPA {@code lessThan} 与全日窗
     * {@code dateTo+1 日 00:00:00} 同口径。
     *
     * @param inclusiveEnd 闭区间止（含）
     * @return 开区间止（不含）；入参 null 则 null
     */
    public static LocalDateTime toExclusiveEnd(LocalDateTime inclusiveEnd) {
        if (inclusiveEnd == null) {
            return null;
        }
        return inclusiveEnd.plusSeconds(1);
    }

    /**
     * 自然日默认窗起文案（当日 00:00:00）。
     *
     * @param day 自然日
     * @return {@code yyyy-MM-dd HH:mm:ss}
     */
    public static String formatDayStart(LocalDate day) {
        return day.atStartOfDay().format(GeneralSharedConstants.DATE_TIME_FORMATTER);
    }

    /**
     * 自然日默认窗止文案（当日 23:59:59）。
     *
     * @param day 自然日
     * @return {@code yyyy-MM-dd HH:mm:ss}
     */
    public static String formatDayEnd(LocalDate day) {
        return day.atTime(LocalTime.of(23, 59, 59)).format(GeneralSharedConstants.DATE_TIME_FORMATTER);
    }

    /**
     * 将原始文本规范为闭区间边界时刻。
     *
     * @param rawText    原始起/止文案
     * @param startBound true=窗起（日仅补 00:00:00）；false=窗止（日仅补 23:59:59）
     * @return 本地时刻
     */
    private static LocalDateTime parseBound(String rawText, boolean startBound) {
        if (rawText == null) {
            throw new DateTimeParseException("report date window text is blank", "", 0);
        }
        String text = rawText.trim();
        if (text.isEmpty()) {
            throw new DateTimeParseException("report date window text is blank", text, 0);
        }
        String normalized = text.replace('T', ' ');
        // 仅日历日：按起止边界补全日默认时刻
        if (normalized.length() == GeneralSharedConstants.DATE_DISPLAY_PATTERN.length()) {
            LocalDate date = LocalDate.parse(normalized, GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            if (startBound) {
                return date.atStartOfDay();
            }
            return date.atTime(LocalTime.of(23, 59, 59));
        }
        // 缺秒时补 :00，与前端 value-format / ISO 局部对齐
        if (normalized.length() == 16) {
            normalized = normalized + ":00";
        }
        return LocalDateTime.parse(normalized, GeneralSharedConstants.DATE_TIME_FORMATTER);
    }
}
