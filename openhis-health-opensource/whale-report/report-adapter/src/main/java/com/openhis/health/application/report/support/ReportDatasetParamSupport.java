/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.support;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReportDatasetParamSupport {

    private ReportDatasetParamSupport() {
    }

    public static Map<String, Object> copyParams(Map<String, Object> params) {
        if (params != null) {
            return new LinkedHashMap<>(params);
        }
        return new LinkedHashMap<>();
    }

    public static String trimReportCode(String reportCode) {
        if (reportCode != null) {
            return reportCode.trim();
        }
        return "";
    }

    public static LocalDate resolveDate(Map<String, Object> params, String... keys) {
        if (params == null) {
            return null;
        }
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof LocalDate date) {
                return date;
            }
            if (raw instanceof LocalDateTime dateTime) {
                return dateTime.toLocalDate();
            }
            if (raw instanceof String text && StringUtils.isNotBlank(text)) {
                try {
                    return LocalDate.parse(text.trim());
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return null;
    }

    public static LocalDate resolveDateRangeFrom(Map<String, Object> params, LocalDate defaultDate) {
        LocalDate from = resolveDate(params, "dateFrom", "date_from");
        if (from != null) {
            return from;
        }
        return defaultDate;
    }

    public static LocalDate resolveDateRangeTo(Map<String, Object> params, LocalDate dateFrom) {
        LocalDate to = resolveDate(params, "dateTo", "date_to");
        if (to == null) {
            return dateFrom;
        }
        if (to.isBefore(dateFrom)) {
            return dateFrom;
        }
        return to;
    }

    public static LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    public static LocalDateTime endOf(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }

    public static String formatDateRange(LocalDate from, LocalDate to) {
        if (from.equals(to)) {
            return from.toString();
        }
        return from + "~" + to;
    }
}
