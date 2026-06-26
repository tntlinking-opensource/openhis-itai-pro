/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.common.validator;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.PlatformTenantAccessPolicy;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 人员岗位维护写路径：断言目标 org/location 落在操作者岗位管辖范围内。
 */
@Component
@RequiredArgsConstructor
public class StaffManagedScopeWriteGate {

    private final ICurrentUser currentUser;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final PlatformTenantAccessPolicy platformTenantAccessPolicy;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;

    /**
     * 校验 org 型岗位块写入目标均在操作者管辖科室内。
     */
    public void assertWritableOrganizationIds(Collection<Long> organizationIds) {
        if (!this.isWriteScopeEnforcementActive()) {
            return;
        }
        if (organizationIds == null || organizationIds.isEmpty()) {
            return;
        }
        var managedOrgIds = managedOrganizationIdsResolver.managedOrganizationUnitIdSetOrEmpty();
        if (managedOrgIds.isEmpty()) {
            return;
        }
        for (Long organizationId : organizationIds) {
            if (organizationId == null) {
                continue;
            }
            if (!managedOrgIds.contains(organizationId)) {
                throw new BusinessException(
                    ErrorCodeConstants.MANAGED_SCOPE_OUT_OF_SCOPE,
                    "目标科室不在您的岗位管辖范围内");
            }
        }
    }

    /**
     * 校验 location 型岗位块（药库/药房/病区）写入目标均在操作者管辖 {@code adm_location.id} 内。
     */
    public void assertWritableLocationIds(Collection<Long> locationIds) {
        if (!this.isWriteScopeEnforcementActive()) {
            return;
        }
        if (locationIds == null || locationIds.isEmpty()) {
            return;
        }
        var managedLocationIds = managedLocationIdsResolver.managedLocationIdSetOrEmpty();
        if (managedLocationIds.isEmpty()) {
            return;
        }
        for (Long locationId : locationIds) {
            if (locationId == null) {
                continue;
            }
            if (!managedLocationIds.contains(locationId)) {
                throw new BusinessException(
                    ErrorCodeConstants.MANAGED_SCOPE_OUT_OF_SCOPE,
                    "目标库房不在您的岗位管辖范围内");
            }
        }
    }

    private boolean isWriteScopeEnforcementActive() {
        if (TenantUtils.isHost()) {
            return false;
        }
        if (isOperatorExemptFromWriteScopeEnforcement()) {
            return false;
        }
        return currentUser.isPractitionerDataScopeActive();
    }

    /**
     * 平台管理员可维护全员岗位，与 {@code PositionAppService#resolveEditableScopes} 口径一致。
     */
    private boolean isOperatorExemptFromWriteScopeEnforcement() {
        Long operatorId = currentOperatorResolver.findOperatorIdOrNull();
        if (operatorId == null) {
            return false;
        }
        return platformTenantAccessPolicy != null && platformTenantAccessPolicy.isPlatformAdmin(operatorId);
    }
}
