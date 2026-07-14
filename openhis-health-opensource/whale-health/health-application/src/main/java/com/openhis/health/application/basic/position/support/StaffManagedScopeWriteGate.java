/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.position.support;

import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;

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
     *
     * @param organizationIds 待写入科室主键集合
     * @throws BusinessException 任一科室越权时
     */
    public void assertWritableOrganizationIds(Collection<Long> organizationIds) {
        if (!this.isWriteScopeEnforcementActive()) {
            return;
        }
        if (organizationIds == null || organizationIds.isEmpty()) {
            return;
        }
        var managedOrgIds = managedOrganizationIdsResolver.managedOrganizationUnitIdSetOrEmpty();
        // 操作者未绑定岗位科室：允许维护（与读路径回落口径一致）
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
     *
     * @param locationIds 待写入库位主键集合
     * @throws BusinessException 任一库位越权时
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

    /**
     * 是否启用写操作管辖范围校验：Host 租户、豁免操作员、未激活 dataScope 时不校验。
     *
     * @return true 表示须 enforce 写范围
     */
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
