/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.web;

import com.zxplat.framework.core.application.ILoginUserOrganizationUnitSync;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.UUID;

/**
 * 全局请求拦截器：写入 MDC 请求 ID，并在请求级清理 {@link ICurrentUser} 的 ThreadLocal 缓存。
 *
 * <p>本拦截器注册顺序早于 {@link com.zxplat.framework.core.infrastructure.security.OrganizationUnitContextInterceptor}，
 * 保证登录/鉴权路径也会清空岗位管辖缓存，避免线程复用串用户。</p>
 */
@Component
public class RequestIdInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC = "requestId";

    @Autowired
    private ICurrentUser currentUser;

    @Autowired
    private ObjectProvider<ILoginUserOrganizationUnitSync> loginUserOrganizationUnitSync;

    @Autowired
    private ObjectProvider<List<RequestScopeCleaner>> requestScopeCleaners;

    /**
     * 为当前请求绑定 trace id，并在入口清理请求级 ThreadLocal 缓存。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler  目标处理器
     * @return 始终 {@code true}，不阻断请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 只清岗位管辖等请求级缓存；禁止在此清 RequestScopeCleaner。
        // JWT 过滤器已写入 LoginSessionIdRequestContext，入口再清会导致多浏览器会话回落到 userId 串科。
        clearEntryCaches();

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(REQUEST_ID_MDC, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        return true;
    }

    /**
     * 请求结束后清理 MDC 与请求级 ThreadLocal 缓存。
     *
     * @param request  当前 HTTP 请求
     * @param response  当前 HTTP 响应
     * @param handler   目标处理器
     * @param ex        处理过程中抛出的异常；无异常时为 {@code null}
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(REQUEST_ID_MDC);
        clearEntryCaches();
        clearRequestScopeCleaners();
    }

    /**
     * 请求入口/结束：清岗位管辖与登录科室同步的 ThreadLocal（不含会话实例上下文）。
     */
    private void clearEntryCaches() {
        currentUser.clearRequestCache();
        ILoginUserOrganizationUnitSync sync = loginUserOrganizationUnitSync.getIfAvailable();
        if (sync != null) {
            sync.clearRequestCache();
        }
    }

    /**
     * 请求结束才清 {@link RequestScopeCleaner}（含登录会话实例、会话行请求缓存）。
     */
    private void clearRequestScopeCleaners() {
        List<RequestScopeCleaner> cleaners = requestScopeCleaners.getIfAvailable();
        if (cleaners != null) {
            for (RequestScopeCleaner cleaner : cleaners) {
                if (cleaner != null) {
                    cleaner.clearRequestScope();
                }
            }
        }
    }
}
