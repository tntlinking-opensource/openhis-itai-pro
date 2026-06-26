/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.support;

import com.openhis.chs.api.ResultBody;
import org.apache.commons.lang3.StringUtils;

/**
 * CHS 模块字符串与平台应答辅助（禁三元时的共用 if-else 落点）。
 */
public final class ChsTextSupport {

    private ChsTextSupport() {
    }

    /**
     * 空白或 null 转为 null，否则返回 trim 后字符串。
     *
     * @param value 原始值
     * @return trim 后非空串，或 null
     */
    public static String blankToNull(String value) {
        return com.openhis.whale.core.application.util.StringUtils.trimToNull(value);
    }

    /**
     * 正数则返回原值，否则返回默认值（用于 HTTP 超时等配置兜底）。
     *
     * @param value        配置值
     * @param defaultValue 默认值
     * @return 有效正数或 defaultValue
     */
    public static int positiveOrDefault(int value, int defaultValue) {
        if (value > 0) {
            return value;
        }
        return defaultValue;
    }

    /**
     * 异常消息非空则返回，否则返回 fallback（类名或固定文案）。
     *
     * @param ex       异常
     * @param fallback fallback
     * @return 展示用错误信息
     */
    public static String exceptionMessageOr(Throwable ex, String fallback) {
        if (ex != null && ex.getMessage() != null) {
            return ex.getMessage();
        }
        return fallback;
    }

    /**
     * 平台应答 err_msg；body 为空时返回 emptyFallback。
     *
     * @param body           平台应答
     * @param emptyFallback  body 为 null 时的文案
     * @return 错误信息
     */
    public static String platformErrMsg(ResultBody body, String emptyFallback) {
        if (body == null) {
            return emptyFallback;
        }
        if (StringUtils.isNotBlank(body.getErrMsg())) {
            return body.getErrMsg().trim();
        }
        return emptyFallback;
    }

    /**
     * 去掉 URL 末尾斜杠。
     *
     * @param url 原始 URL
     * @return 无尾斜杠的 URL
     */
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

    /**
     * 路径不以 {@code /} 开头则补上。
     *
     * @param path 相对路径
     * @return 以 {@code /} 开头的路径
     */
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
