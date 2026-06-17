package com.openhis.whale.report.application.query.support;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 报表查询分页与日期区间格式化（query 包内共用，禁三元）。
 */
public final class ReportQueryPaging {

    private ReportQueryPaging() {
    }

    public static <T> List<T> slice(List<T> allRows, int from, int to) {
        if (from >= allRows.size()) {
            return List.of();
        }
        return allRows.subList(from, to);
    }

    public static String trimToEmpty(String value) {
        if (value != null) {
            return value.trim();
        }
        return "";
    }

    public static String formatDateRange(String dateFrom, String dateTo) {
        String from = trimToEmpty(dateFrom);
        String to = trimToEmpty(dateTo);
        if (StringUtils.hasText(from) && StringUtils.hasText(to)) {
            if (from.equals(to)) {
                return from;
            }
            return from + "~" + to;
        }
        if (StringUtils.hasText(from)) {
            return from;
        }
        return "—";
    }
}
