package com.openhis.whale.report.application.report.support;

import com.openhis.whale.core.application.util.StringUtils;

import java.util.List;

/**
 * 报表查询分页与日期区间格式化（query 包内共用，禁三元）。
 */
public final class ReportQueryPaging {

    private ReportQueryPaging() {
    }

    /**
     * 内存分页切片（from 含，to 不含）。
     *
     * @param allRows 全量行
     * @param from    起始下标
     * @param to      结束下标（不含）
     */
    public static <T> List<T> slice(List<T> allRows, int from, int to) {
        if (from >= allRows.size()) {
            // 起始下标超出范围，返回空页
            return List.of();
        }
        return allRows.subList(from, to);
    }

    /**
     * null 安全 trim，null 返回空串。
     *
     * @param value 原始字符串
     * @return trim 后的字符串，可能为空串
     */
    public static String trimToEmpty(String value) {
        if (value != null) {
            return value.trim();
        }
        return "";
    }

    /**
     * 格式化日期区间为表头占位符 {@code {{dateRange}}}。
     *
     * @param dateFrom 开始日期字符串
     * @param dateTo   结束日期字符串
     * @return 展示用日期范围，缺省时 {@code —}
     */
    public static String formatDateRange(String dateFrom, String dateTo) {
        String from = trimToEmpty(dateFrom);
        String to = trimToEmpty(dateTo);
        if (StringUtils.isNotEmpty(from) && StringUtils.isNotEmpty(to)) {
            if (from.equals(to)) {
                return from;
            }
            return from + "~" + to;
        }
        if (StringUtils.isNotEmpty(from)) {
            return from;
        }
        return "—";
    }
}
