/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.support;

import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * adapter 层报表请求参数解析（日期、Long 主键、参数拷贝）。
 */
public final class ReportDatasetParamSupport {

    private ReportDatasetParamSupport() {
    }

    /**
     * 浅拷贝查询参数 Map，避免下游修改影响原始请求。
     *
     * @param params 原始参数，可为 null
     * @return 非 null 的可变副本
     */
    public static Map<String, Object> copyParams(Map<String, Object> params) {
        if (params != null) {
            return new LinkedHashMap<>(params);
        }
        return new LinkedHashMap<>();
    }

    /**
     * 规范化报表编码（trim，null 转空串）。
     *
     * @param reportCode 原始编码
     * @return 去首尾空白后的编码，可能为空串
     */
    public static String trimReportCode(String reportCode) {
        return StringUtils.trimToEmpty(reportCode);
    }

    /**
     * 从参数 Map 中按候选键解析 {@link LocalDate}。
     *
     * @param params 查询参数
     * @param keys   候选键名（按顺序尝试）
     * @return 解析成功返回日期，否则 null
     */
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
            if (raw instanceof String text && StringUtils.isNotEmpty(text)) {
                try {
                    return LocalDate.parse(text.trim());
                } catch (Exception ignored) {
                    // 当前键格式无效，尝试下一候选键
                }
            }
        }
        return null;
    }

    /**
     * 解析日期范围起始日，缺省回退 defaultDate。
     *
     * @param params      查询参数
     * @param defaultDate 缺省起始日
     * @return 非 null 起始日
     */
    public static LocalDate resolveDateRangeFrom(Map<String, Object> params, LocalDate defaultDate) {
        LocalDate from = resolveDate(params, "dateFrom", "date_from");
        if (from != null) {
            return from;
        }
        return defaultDate;
    }

    /**
     * 解析日期范围结束日；若早于起始日则钳制为起始日。
     *
     * @param params   查询参数
     * @param dateFrom 已解析的起始日
     * @return 非 null 结束日
     */
    public static LocalDate resolveDateRangeTo(Map<String, Object> params, LocalDate dateFrom) {
        LocalDate to = resolveDate(params, "dateTo", "date_to");
        if (to == null) {
            return dateFrom;
        }
        if (to.isBefore(dateFrom)) {
            // 结束早于开始，收敛为单日范围
            return dateFrom;
        }
        return to;
    }

    /**
     * 日期当天 00:00:00。
     *
     * @param date 日历日
     * @return 当天起始时刻
     */
    public static LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    /**
     * 日期当天 23:59:59.999999999。
     *
     * @param date 日历日
     * @return 当天结束时刻
     */
    public static LocalDateTime endOf(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }

    /**
     * 格式化日期范围为展示字符串（同日仅显示一天）。
     *
     * @param from 起始日
     * @param to   结束日
     * @return 展示用日期范围
     */
    public static String formatDateRange(LocalDate from, LocalDate to) {
        if (from.equals(to)) {
            return from.toString();
        }
        return from + "~" + to;
    }

    /**
     * 从参数 Map 中按候选键解析 Long 主键。
     *
     * @param params 查询参数
     * @param keys   候选键名
     * @return 解析成功返回 Long，否则 null
     */
    public static Long extractLong(Map<String, Object> params, String... keys) {
        if (params == null) {
            return null;
        }
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Number number) {
                return number.longValue();
            }
            if (raw instanceof String text && StringUtils.isNotEmpty(text)) {
                try {
                    return Long.parseLong(text.trim());
                } catch (NumberFormatException ignored) {
                    // 当前键非数字，尝试下一候选键
                }
            }
        }
        return null;
    }

    /**
     * 从参数 Map 中按候选键解析 Long 主键列表（Collection 元素）。
     *
     * @param params 查询参数
     * @param keys   候选键名
     * @return 解析出的 ID 列表，无匹配时空列表
     */
    @SuppressWarnings("unchecked")
    public static List<Long> extractLongList(Map<String, Object> params, String... keys) {
        if (params == null) {
            return List.of();
        }
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof Collection<?> collection) {
                List<Long> out = new ArrayList<>();
                for (Object item : collection) {
                    if (item instanceof Number number) {
                        out.add(number.longValue());
                    } else if (item instanceof String text && StringUtils.isNotEmpty(text)) {
                        try {
                            out.add(Long.parseLong(text.trim()));
                        } catch (NumberFormatException ignored) {
                            // 跳过非法元素
                        }
                    }
                }
                return out;
            }
        }
        return List.of();
    }
}
