/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.util;

/**
 * 字符串工具类
 */
public class StringUtils {

    /**
     * 判断字符串是否为空（null或空字符串）
     * @param str 字符串
     * @return true表示为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空
     * @param str 字符串
     * @return true表示不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串是否为 blank（{@code null} 或 trim 后为空）。
     *
     * @param str 字符串
     * @return true 表示 blank
     */
    public static boolean isBlank(String str) {
        return isEmpty(str);
    }

    /**
     * 判断字符串是否不为 blank（与 Apache {@code StringUtils#isNotBlank} 语义一致）。
     *
     * @param str 字符串
     * @return true 表示非 blank
     */
    public static boolean isNotBlank(String str) {
        return !isEmpty(str);
    }

    /**
     * 如果字符串为null，返回空字符串
     * @param str 字符串
     * @return 非null的字符串
     */
    public static String nullToEmpty(String str) {
        return str == null ? "" : str;
    }

    /**
     * 如果字符串为空，返回默认值
     * @param str 字符串
     * @param defaultValue 默认值
     * @return 字符串或默认值
     */
    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    /**
     * 截取字符串（安全截取，不会抛出异常）
     * @param str 字符串
     * @param maxLength 最大长度
     * @return 截取后的字符串
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }

    /**
     * 去掉首尾空白并将空串视为 {@code null}，避免业务层把空白当合法值入库或参与比较。
     *
     * @param value 原始字符串，可为 {@code null}
     * @return trim 后非空串，否则 {@code null}
     */
    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 去掉首尾空白；{@code null} 或仅空白时返回空串（展示/比较键场景）。
     *
     * @param value 原始字符串，可为 {@code null}
     * @return trim 后字符串，空白时为 {@code ""}
     */
    public static String trimToEmpty(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "";
        }
        return trimmed;
    }

    /**
     * 在多个候选中取首个非空白（trim 后）；均为空白时返回 {@code null}。
     *
     * @param primary  优先候选
     * @param fallback 次选候选
     * @return 首个非空白 trim 结果，或 {@code null}
     */
    public static String firstNonBlank(String primary, String fallback) {
        String first = trimToNull(primary);
        if (first != null) {
            return first;
        }
        return trimToNull(fallback);
    }

    /**
     * 在多个候选中取首个非空白（trim 后）；均为空白时返回 {@code null}。
     *
     * @param values 候选值，可为 {@code null} 数组
     * @return 首个非空白 trim 结果，或 {@code null}
     */
    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * 优先取 primary（trim 后非 blank）；否则回落为 {@link #nullToEmpty}（fallback）。
     *
     * @param primary  优先候选
     * @param fallback 次选候选
     * @return 非 blank 的 primary trim 结果，或 fallback（null 时为 {@code ""}）
     */
    public static String firstNonBlankOrEmpty(String primary, String fallback) {
        String first = trimToNull(primary);
        if (first != null) {
            return first;
        }
        return nullToEmpty(fallback);
    }

    /**
     * 字符串 trim 后若仍为 blank，则返回默认值。
     *
     * @param value        原始值
     * @param defaultValue blank 时的默认值
     * @return trim 后非 blank 串，或 defaultValue
     */
    public static String defaultIfBlank(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            return trimmed;
        }
        return defaultValue;
    }

    /** Apache {@code StringUtils#defaultString} 兼容：null → 空串。 */
    public static String defaultString(String str) {
        return nullToEmpty(str);
    }

    /** Apache {@code StringUtils#defaultString} 兼容：null → defaultStr。 */
    public static String defaultString(String str, String defaultStr) {
        return str == null ? defaultStr : str;
    }

    /**
     * 正数则返回原值，否则返回默认值（HTTP 超时等配置兜底）。
     */
    public static int positiveOrDefault(int value, int defaultValue) {
        if (value > 0) {
            return value;
        }
        return defaultValue;
    }

    /**
     * 异常消息非空则返回，否则返回 fallback。
     */
    public static String exceptionMessageOr(Throwable ex, String fallback) {
        if (ex != null && ex.getMessage() != null) {
            return ex.getMessage();
        }
        return fallback;
    }

    /** 去掉 URL 末尾斜杠；null/blank 原样返回。 */
    public static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** 路径不以 {@code /} 开头则补上；null/empty 时返回 {@code "/"}。 */
    public static String ensureLeadingSlash(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (path.startsWith("/")) {
            return path;
        }
        return "/" + path;
    }
}
