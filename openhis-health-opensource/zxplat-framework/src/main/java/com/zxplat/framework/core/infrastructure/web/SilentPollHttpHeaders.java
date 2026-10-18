/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 高频静默轮询 HTTP 约定：请求带本头时访问摘要与入参日志应跳过，避免刷屏。
 */
public final class SilentPollHttpHeaders {

    /** 请求头名：值为 {@link #VALUE} 时表示本请求为静默轮询。 */
    public static final String NAME = "X-Zxplat-Silent-Poll";

    /** 请求头取值：启用静默（不写访问/入参日志）。 */
    public static final String VALUE = "1";

    private SilentPollHttpHeaders() {
    }

    /**
     * 当前请求是否标记为静默轮询。
     *
     * @return true 表示应跳过访问摘要与入参日志
     */
    public static boolean isCurrentRequestSilentPoll() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        return isSilentPoll(attributes.getRequest());
    }

    /**
     * 给定请求是否标记为静默轮询。
     *
     * @param request HTTP 请求；空则 false
     * @return true 表示应跳过访问摘要与入参日志
     */
    public static boolean isSilentPoll(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String header = request.getHeader(NAME);
        if (header == null) {
            return false;
        }
        return VALUE.equals(header.trim());
    }
}
