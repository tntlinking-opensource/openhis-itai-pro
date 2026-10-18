/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.session;

import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 登录会话读写封装（B.6.5）：identity / web 层 {@code *AppService} 禁止直注 {@link ICurrentUser}，
 * 认证编排与会话切换须经本组件访问服务端会话 Store。
 */
@Component
@RequiredArgsConstructor
public class LoginSessionContext {

    private final ICurrentUser currentUser;

    public Long getCurrentUserId() {
        return currentUser.getCurrentUserId();
    }

    public String getCurrentUserName() {
        return currentUser.getCurrentUserName();
    }

    public boolean isAuthenticated() {
        return currentUser.isAuthenticated();
    }

    public Long getCurrentOrganizationUnitId() {
        return currentUser.getCurrentOrganizationUnitId();
    }

    public void setCurrentOrganizationUnitId(Long organizationUnitId) {
        currentUser.setCurrentOrganizationUnitId(organizationUnitId);
    }

    public void clearOrganizationUnit() {
        currentUser.clearOrganizationUnit();
    }

    public Long getSessionTenantId() {
        return currentUser.getSessionTenantId();
    }

    public void setSessionTenantId(Long tenantId) {
        currentUser.setSessionTenantId(tenantId);
    }

    public void clearSessionTenant() {
        currentUser.clearSessionTenant();
    }

    public Long getSessionHospitalId() {
        return currentUser.getSessionHospitalId();
    }

    public void setSessionHospitalId(Long hospitalOrganizationId) {
        currentUser.setSessionHospitalId(hospitalOrganizationId);
    }

    public void clearSessionHospital() {
        currentUser.clearSessionHospital();
    }

    public Long getCurrentPractitionerId() {
        return currentUser.getCurrentPractitionerId();
    }

    public List<Long> getManagedLocationIds() {
        return currentUser.getManagedLocationIds();
    }

    public List<Long> getManagedWardLocationIds() {
        return currentUser.getManagedWardLocationIds();
    }

    public List<Long> getManagedOrganizationUnitIds() {
        return currentUser.getManagedOrganizationUnitIds();
    }

    public boolean isPractitionerDataScopeActive() {
        return currentUser.isPractitionerDataScopeActive();
    }

    public Long getImpersonatorUserId() {
        return currentUser.getImpersonatorUserId();
    }

    public String getImpersonatorUserName() {
        return currentUser.getImpersonatorUserName();
    }

    public Long getImpersonatorTenantId() {
        return currentUser.getImpersonatorTenantId();
    }

    public boolean isImpersonating() {
        return currentUser.isImpersonating();
    }
}
