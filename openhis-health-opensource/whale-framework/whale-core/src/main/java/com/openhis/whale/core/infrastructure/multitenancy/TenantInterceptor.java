/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.multitenancy;

import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器：从服务端会话注入当前租户，不信任客户端 {@code X-Tenant-Id}。
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TenantInterceptor.class);
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private ICurrentUser currentUser;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestUri = request.getRequestURI();
        Long sessionTenantId = null;

        if (currentUser.isAuthenticated()) {
            sessionTenantId = currentUser.getSessionTenantId();
            if (sessionTenantId != null) {
                tenantContext.setCurrentTenantId(sessionTenantId);
                warnIfClientHeaderMismatch(request, sessionTenantId, requestUri);
                logger.debug("租户上下文已从会话注入: tenantId={}, URI={}", sessionTenantId, requestUri);
                return true;
            }
        }

        String tenantIdHeader = request.getHeader(TENANT_ID_HEADER);
        if (tenantIdHeader != null && !tenantIdHeader.trim().isEmpty()) {
            if (currentUser.isAuthenticated()) {
                logger.warn(
                    "已登录但未建立会话租户，忽略客户端 X-Tenant-Id={}，请先调用 selectTenant, URI={}",
                    tenantIdHeader,
                    requestUri);
            } else {
                logger.debug("未登录请求携带 X-Tenant-Id，忽略: URI={}", requestUri);
            }
        } else if (currentUser.isAuthenticated()) {
            logger.debug("已登录但会话租户为空, URI={}", requestUri);
        }

        return true;
    }

    private void warnIfClientHeaderMismatch(
            HttpServletRequest request, Long sessionTenantId, String requestUri) {
        String tenantIdHeader = request.getHeader(TENANT_ID_HEADER);
        if (tenantIdHeader == null || tenantIdHeader.trim().isEmpty()) {
            return;
        }
        try {
            Long headerTenantId = Long.parseLong(tenantIdHeader.trim());
            if (!sessionTenantId.equals(headerTenantId)) {
                logger.warn(
                    "客户端 X-Tenant-Id={} 与会话租户 {} 不一致，以会话为准, URI={}",
                    headerTenantId,
                    sessionTenantId,
                    requestUri);
            }
        } catch (NumberFormatException ex) {
            logger.warn("无效的 X-Tenant-Id 格式: {}, URI={}", tenantIdHeader, requestUri);
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        tenantContext.clear();
    }
}
