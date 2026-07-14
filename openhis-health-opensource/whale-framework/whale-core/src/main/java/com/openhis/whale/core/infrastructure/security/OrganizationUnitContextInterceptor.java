/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.security;

import com.openhis.whale.core.application.ILoginDefaultOrganizationResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openhis.whale.core.infrastructure.web.RequestScopeCleaner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 科室上下文拦截器：每请求从 {@link OrganizationUnitSessionStore} 注入到 {@link OrganizationUnitRequestContext}，
 * 请求结束清理请求上下文，避免 ThreadLocal 跨请求泄漏。
 */
@Component
public class OrganizationUnitContextInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationUnitContextInterceptor.class);

    /** 可选：前端显式声明当前科室，须与 SessionStore 一致（不一致时以会话为准并告警）。 */
    public static final String ORGANIZATION_UNIT_ID_HEADER = "X-Organization-Unit-Id";

    @Autowired
    private ICurrentUser currentUser;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private OrganizationUnitSessionStore organizationUnitSessionStore;

    @Autowired(required = false)
    private ILoginDefaultOrganizationResolver loginDefaultOrganizationResolver;

    @Autowired(required = false)
    private List<RequestScopeCleaner> requestScopeCleaners;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 请求入口先清空岗位管辖 ThreadLocal，避免 /auth/* 等未走科室拦截器的路径串用户。
        currentUser.clearRequestCache();
        OrganizationUnitRequestContext.clear();

        if (!currentUser.isAuthenticated()) {
            return true;
        }

        Long userId = currentUser.getCurrentUserId();
        Long tenantId = null;
        if (tenantContext != null) {
            tenantId = tenantContext.getCurrentTenantId();
        }
        if (userId == null || tenantId == null) {
            return true;
        }

        Long sessionOrgId = organizationUnitSessionStore.get(userId, tenantId);
        if (sessionOrgId != null && currentUser.isPractitionerDataScopeActive()) {
            List<Long> managedOrganizationUnitIds = currentUser.getManagedOrganizationUnitIds();
            boolean sessionAllowed = true;
            if (managedOrganizationUnitIds != null && !managedOrganizationUnitIds.isEmpty()) {
                sessionAllowed = false;
                for (Long managedOrgId : managedOrganizationUnitIds) {
                    if (sessionOrgId.equals(managedOrgId)) {
                        sessionAllowed = true;
                        break;
                    }
                }
            }
            if (!sessionAllowed) {
                Long defaultLoginOrgId = resolveDefaultLoginOrganizationId(userId);
                if (defaultLoginOrgId != null && defaultLoginOrgId.equals(sessionOrgId)) {
                    sessionAllowed = true;
                }
            }
            if (!sessionAllowed) {
                logger.warn(
                    "会话科室 {} 不在岗位管辖范围，已清除, userId={}, tenantId={}, URI={}",
                    sessionOrgId,
                    userId,
                    tenantId,
                    request.getRequestURI());
                organizationUnitSessionStore.remove(userId, tenantId);
                sessionOrgId = null;
            }
        }
        if (sessionOrgId != null) {
            OrganizationUnitRequestContext.setOrganizationUnitId(sessionOrgId);
        }

        String headerOrgId = request.getHeader(ORGANIZATION_UNIT_ID_HEADER);
        if (headerOrgId != null && !headerOrgId.trim().isEmpty()) {
            try {
                Long headerOrg = Long.parseLong(headerOrgId.trim());
                if (sessionOrgId == null) {
                    logger.warn(
                            "请求头 {}={} 但会话中无已选科室, URI={}",
                            ORGANIZATION_UNIT_ID_HEADER,
                            headerOrgId,
                            request.getRequestURI());
                } else if (!sessionOrgId.equals(headerOrg)) {
                    logger.warn(
                            "请求头 {}={} 与会话科室 {} 不一致, 以会话为准, URI={}",
                            ORGANIZATION_UNIT_ID_HEADER,
                            headerOrgId,
                            sessionOrgId,
                            request.getRequestURI());
                }
            } catch (NumberFormatException ex) {
                logger.warn(
                        "无效的 {} 格式: {}, URI={}",
                        ORGANIZATION_UNIT_ID_HEADER,
                        headerOrgId,
                        request.getRequestURI());
            }
        }

        if (logger.isDebugEnabled() && sessionOrgId != null) {
            logger.debug(
                    "科室请求上下文已注入: userId={}, tenantId={}, organizationUnitId={}, URI={}",
                    userId,
                    tenantId,
                    sessionOrgId,
                    request.getRequestURI());
        }

        return true;
    }

    private Long resolveDefaultLoginOrganizationId(Long userId) {
        if (loginDefaultOrganizationResolver == null || userId == null) {
            return null;
        }
        return loginDefaultOrganizationResolver.resolveDefaultLoginOrganizationId(
                userId, currentUser.getSessionHospitalId())
            .orElse(null);
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        OrganizationUnitRequestContext.clear();
        // 与 preHandle 对称，释放本请求的岗位管辖 ThreadLocal。
        currentUser.clearRequestCache();
        if (requestScopeCleaners != null) {
            for (RequestScopeCleaner cleaner : requestScopeCleaners) {
                cleaner.clearRequestScope();
            }
        }
    }
}
