/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.web;

import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 访问摘要拦截器：每请求一行日志，并写入 {@code tenantId}/{@code userId} 到 MDC。
 *
 * <p>注册顺序须晚于 {@link com.zxplat.framework.core.infrastructure.multitenancy.TenantInterceptor}，
 * 以便日志与 MDC 能反映会话租户与用户身份。</p>
 */
@Component
public class ApiAccessLogInterceptor implements HandlerInterceptor {

    /** 独立 logger 名，便于在配置中单独开关或过滤。 */
    public static final String ACCESS_LOGGER_NAME = "zxplat.access";

    private static final Logger accessLogger = LoggerFactory.getLogger(ACCESS_LOGGER_NAME);

    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_USER_ID = "userId";
    private static final String REQUEST_START_TIME_ATTR = "zxplat.access.startTime";

    private static final long SLOW_REQUEST_THRESHOLD_MS = 3_000L;

    @Autowired
    private ICurrentUser currentUser;

    /**
     * 记录起始时间，并将当前用户/租户写入 MDC，供后续业务日志串联。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(REQUEST_START_TIME_ATTR, System.currentTimeMillis());
        bindUserAndTenantToMdc();
        return true;
    }

    /**
     * 输出一行访问摘要；异常或慢请求提升为 WARN。
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            if (!shouldLog(request)) {
                return;
            }

            long durationMs = resolveDurationMs(request);
            int status = response.getStatus();
            String summary = buildSummary(request, status, durationMs);

            if (ex != null) {
                accessLogger.warn("{} err={}", summary, ex.getMessage());
            } else if (status >= 400) {
                accessLogger.warn("{}", summary);
            } else if (durationMs >= SLOW_REQUEST_THRESHOLD_MS) {
                accessLogger.warn("SLOW {}", summary);
            } else {
                accessLogger.info("{}", summary);
            }
        } finally {
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    private void bindUserAndTenantToMdc() {
        if (currentUser.isAuthenticated()) {
            Long userId = currentUser.getCurrentUserId();
            if (userId != null) {
                MDC.put(MDC_USER_ID, String.valueOf(userId));
            }
        }
        Long tenantId = currentUser.getSessionTenantId();
        if (tenantId != null) {
            MDC.put(MDC_TENANT_ID, String.valueOf(tenantId));
        }
    }

    private boolean shouldLog(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        // 静默轮询：跳过访问摘要，避免护士站等页高频刷日志
        if (SilentPollHttpHeaders.isSilentPoll(request)) {
            return false;
        }
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return false;
        }
        return !uri.startsWith("/actuator");
    }

    private long resolveDurationMs(HttpServletRequest request) {
        Object startTime = request.getAttribute(REQUEST_START_TIME_ATTR);
        if (startTime instanceof Long start) {
            return Math.max(0L, System.currentTimeMillis() - start);
        }
        return 0L;
    }

    private String buildSummary(HttpServletRequest request, int status, long durationMs) {
        return String.format(
            "%s %s -> %d %dms user=%s tenant=%s",
            request.getMethod(),
            request.getRequestURI(),
            status,
            durationMs,
            formatId(currentUser.getCurrentUserId()),
            formatId(currentUser.getSessionTenantId()));
    }

    private static String formatId(Long id) {
        return id != null ? String.valueOf(id) : "-";
    }
}
