/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.security;

import com.zxplat.framework.core.application.IPractitionerManagedScopeResolver;
import com.zxplat.framework.core.application.dto.PractitionerManagedScope;
import com.zxplat.framework.core.infrastructure.multitenancy.HospitalSessionStore;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantContext;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantSessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

/**
 * 当前用户实现：身份来自 Spring Security；科室会话经 {@link OrganizationUnitSessionStore} 持久化；
 * 数据管辖范围经 {@link IPractitionerManagedScopeResolver} 从 {@code adm_pr_assign} 派生（health 模块提供）：
 * 药库/药房为 {@code adm_location.id}，科室为 {@code adm_organization.id}，病区为 {@code adm_location.id}（WARD）。
 */
public class CurrentUser implements ICurrentUser {

    private final TenantContext tenantContext;
    private final OrganizationUnitSessionStore organizationUnitSessionStore;
    private final TenantSessionStore tenantSessionStore;
    private final HospitalSessionStore hospitalSessionStore;
    private final IPractitionerManagedScopeResolver practitionerManagedScopeResolver;

    public CurrentUser(
            TenantContext tenantContext,
            OrganizationUnitSessionStore organizationUnitSessionStore,
            TenantSessionStore tenantSessionStore,
            HospitalSessionStore hospitalSessionStore,
            @Autowired(required = false) IPractitionerManagedScopeResolver practitionerManagedScopeResolver) {
        this.tenantContext = tenantContext;
        this.organizationUnitSessionStore = organizationUnitSessionStore;
        this.tenantSessionStore = tenantSessionStore;
        this.hospitalSessionStore = hospitalSessionStore;
        this.practitionerManagedScopeResolver = practitionerManagedScopeResolver;
    }

    /**
     * 本请求内岗位管辖解析缓存。
     *
     * <p>挂载在 {@link ThreadLocal} 上，避免 {@code CurrentUser} 单例在线程池复用时串读上一用户的管辖范围。</p>
     */
    private static final class RequestCache {
        /** 已解析的管辖范围；未解析或解析失败时为 {@link PractitionerManagedScope#EMPTY}。 */
        PractitionerManagedScope cachedManagedScope;
        /** 本请求是否已完成一次管辖范围解析尝试。 */
        boolean managedScopeResolved;
        /** 本请求是否启用岗位派生数据管辖严格模式。 */
        boolean practitionerDataScopeActive;
    }

    private static final ThreadLocal<RequestCache> REQUEST_CACHE = ThreadLocal.withInitial(RequestCache::new);

    @Override
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            if (authentication.getPrincipal() instanceof UserPrincipal) {
                return ((UserPrincipal) authentication.getPrincipal()).getUserId();
            }
        }
        return null;
    }

    @Override
    public String getCurrentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            if (authentication.getPrincipal() instanceof UserPrincipal) {
                return ((UserPrincipal) authentication.getPrincipal()).getUsername();
            }
            return authentication.getName();
        }
        return null;
    }

    @Override
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    @Override
    public Long getCurrentOrganizationUnitId() {
        return OrganizationUnitRequestContext.getOrganizationUnitId();
    }

    @Override
    public void setCurrentOrganizationUnitId(Long organizationUnitId) {
        if (organizationUnitId == null) {
            clearOrganizationUnit();
            return;
        }
        Long userId = getCurrentUserId();
        Long tenantId = null;
        if (tenantContext != null) {
            tenantId = tenantContext.getCurrentTenantId();
        }
        if (userId != null && tenantId != null) {
            organizationUnitSessionStore.put(userId, tenantId, organizationUnitId);
        }
        OrganizationUnitRequestContext.setOrganizationUnitId(organizationUnitId);
    }

    @Override
    public void clearOrganizationUnit() {
        OrganizationUnitRequestContext.clear();
        Long userId = getCurrentUserId();
        Long tenantId = null;
        if (tenantContext != null) {
            tenantId = tenantContext.getCurrentTenantId();
        }
        if (userId != null && tenantId != null) {
            organizationUnitSessionStore.remove(userId, tenantId);
        }
    }

    @Override
    public Long getSessionTenantId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return tenantSessionStore.get(userId);
    }

    @Override
    public void setSessionTenantId(Long tenantId) {
        Long userId = getCurrentUserId();
        if (userId != null && tenantId != null) {
            tenantSessionStore.put(userId, tenantId);
        }
    }

    @Override
    public void clearSessionTenant() {
        Long userId = getCurrentUserId();
        if (userId != null) {
            tenantSessionStore.remove(userId);
        }
    }

    @Override
    public Long getSessionHospitalId() {
        Long userId = getCurrentUserId();
        Long tenantId = null;
        if (tenantContext != null) {
            tenantId = tenantContext.getCurrentTenantId();
        }
        if (userId == null || tenantId == null) {
            return null;
        }
        return hospitalSessionStore.get(userId, tenantId);
    }

    @Override
    public void setSessionHospitalId(Long hospitalOrganizationId) {
        Long userId = getCurrentUserId();
        Long tenantId = null;
        if (tenantContext != null) {
            tenantId = tenantContext.getCurrentTenantId();
        }
        if (userId != null && tenantId != null && hospitalOrganizationId != null) {
            hospitalSessionStore.put(userId, tenantId, hospitalOrganizationId);
        }
    }

    @Override
    public void clearSessionHospital() {
        Long userId = getCurrentUserId();
        Long tenantId = null;
        if (tenantContext != null) {
            tenantId = tenantContext.getCurrentTenantId();
        }
        if (userId != null && tenantId != null) {
            hospitalSessionStore.remove(userId, tenantId);
        }
    }

    @Override
    public Long getCurrentPractitionerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            if (authentication.getPrincipal() instanceof UserPrincipal) {
                return ((UserPrincipal) authentication.getPrincipal()).getPractitionerId();
            }
        }
        return null;
    }

    @Override
    public List<Long> getManagedLocationIds() {
        return resolveManagedScope().managedLocationIds();
    }

    @Override
    public List<Long> getManagedWardLocationIds() {
        return resolveManagedScope().managedWardLocationIds();
    }

    @Override
    public List<Long> getManagedOrganizationUnitIds() {
        return resolveManagedScope().managedOrganizationUnitIds();
    }

    @Override
    public boolean isPractitionerDataScopeActive() {
        resolveManagedScope();
        return REQUEST_CACHE.get().practitionerDataScopeActive;
    }

    /**
     * 清理本请求内岗位管辖解析缓存。
     *
     * <p>须在 HTTP 请求入口（拦截器 {@code preHandle}）与结束（{@code afterCompletion}）调用，
     * 防止 Tomcat 线程复用后仍命中上一用户的 {@link RequestCache}。</p>
     */
    @Override
    public void clearRequestCache() {
        REQUEST_CACHE.remove();
    }

    @Override
    public Long getImpersonatorUserId() {
        UserPrincipal principal = getUserPrincipal();
        return principal != null ? principal.getImpersonatorUserId() : null;
    }

    @Override
    public String getImpersonatorUserName() {
        UserPrincipal principal = getUserPrincipal();
        return principal != null ? principal.getImpersonatorUserName() : null;
    }

    @Override
    public Long getImpersonatorTenantId() {
        UserPrincipal principal = getUserPrincipal();
        return principal != null ? principal.getImpersonatorTenantId() : null;
    }

    private UserPrincipal getUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())
                && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * 按当前登录用户与医务人员解析岗位派生管辖范围；无 resolver 或未登录时返回 {@link PractitionerManagedScope#EMPTY}。
     */
    private PractitionerManagedScope resolveManagedScope() {
        RequestCache cache = REQUEST_CACHE.get();
        // 本请求已解析过：直接返回缓存，避免重复查 adm_pr_assign。
        if (cache.managedScopeResolved) {
            if (cache.cachedManagedScope == null) {
                return PractitionerManagedScope.EMPTY;
            }
            return cache.cachedManagedScope;
        }
        cache.managedScopeResolved = true;
        cache.practitionerDataScopeActive = false;
        // 未登录或未装配 health 解析器：视为无岗位管辖。
        if (!isAuthenticated() || practitionerManagedScopeResolver == null) {
            cache.cachedManagedScope = PractitionerManagedScope.EMPTY;
            return cache.cachedManagedScope;
        }
        Long userId = getCurrentUserId();
        Long practitionerId = getCurrentPractitionerId();
        Long tenantId = null;
        if (tenantContext != null) {
            tenantId = tenantContext.getCurrentTenantId();
        }
        Optional<PractitionerManagedScope> resolved = practitionerManagedScopeResolver
            .resolve(userId, practitionerId, tenantId);
        if (resolved.isPresent()) {
            cache.practitionerDataScopeActive = true;
            cache.cachedManagedScope = resolved.get();
        } else {
            cache.cachedManagedScope = PractitionerManagedScope.EMPTY;
        }
        return cache.cachedManagedScope;
    }
}
