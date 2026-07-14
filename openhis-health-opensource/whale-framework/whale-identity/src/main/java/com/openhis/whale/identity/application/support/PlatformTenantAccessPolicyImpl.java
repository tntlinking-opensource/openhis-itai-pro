/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.support;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.PlatformTenantAccessPolicy;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.identity.domain.Role;
import com.openhis.whale.identity.domain.UserRole;
import com.openhis.whale.identity.domain.UserTenant;
import com.openhis.whale.identity.infrastructure.repository.RoleRepository;
import com.openhis.whale.identity.infrastructure.repository.UserRoleRepository;
import com.openhis.whale.identity.infrastructure.repository.UserTenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 平台 admin（{@code admin} 角色）跨租户运维；业务会话租户来自 {@link TenantSessionStore}，非客户端请求头。
 */
@Component
@RequiredArgsConstructor
public class PlatformTenantAccessPolicyImpl implements PlatformTenantAccessPolicy {

    private static final String ADMIN_ROLE_NAME = "admin";
    private static final String ADMIN_ROLE_CODE = "ROLE_ADMIN";

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserTenantRepository userTenantRepository;

    @Override
    public boolean isPlatformAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        for (UserRole userRole : userRoleRepository.findByUserId(userId)) {
            if (userRole == null || userRole.getRoleId() == null) {
                continue;
            }
            Role role = roleRepository.findById(userRole.getRoleId()).orElse(null);
            if (role == null || Boolean.TRUE.equals(role.getIsDeleted())) {
                continue;
            }
            if (ADMIN_ROLE_NAME.equalsIgnoreCase(role.getName())
                || ADMIN_ROLE_CODE.equalsIgnoreCase(role.getCode())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Long> resolveVisibleTenantIds(Long userId) {
        if (isPlatformAdmin(userId)) {
            return null;
        }
        Set<Long> ids = new LinkedHashSet<>();
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (currentTenantId != null) {
            if (isActiveMember(userId, currentTenantId)) {
                ids.add(currentTenantId);
            }
            return new ArrayList<>(ids);
        }
        for (UserTenant row : userTenantRepository.findByUserId(userId)) {
            if (row != null && !Boolean.TRUE.equals(row.getIsDeleted()) && row.getTenantId() != null) {
                ids.add(row.getTenantId());
            }
        }
        return new ArrayList<>(ids);
    }

    @Override
    public void assertCanManageTenant(Long userId, Long targetTenantId) {
        if (targetTenantId == null) {
            throw new BusinessException("租户ID不能为空");
        }
        if (isPlatformAdmin(userId)) {
            return;
        }
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (currentTenantId == null) {
            throw new BusinessException("请先选择租户后再操作");
        }
        if (!targetTenantId.equals(currentTenantId)) {
            throw new BusinessException("无权操作其他租户，请切换租户或联系平台管理员");
        }
        if (!isActiveMember(userId, targetTenantId)) {
            throw new BusinessException("您不属于该租户");
        }
    }

    @Override
    public void assertCanMaintainTenantCatalog(Long userId) {
        if (!isPlatformAdmin(userId)) {
            throw new BusinessException("仅平台管理员可维护租户目录");
        }
    }

    private boolean isActiveMember(Long userId, Long tenantId) {
        UserTenant row = userTenantRepository.findByUserIdAndTenantId(userId, tenantId);
        return row != null && !Boolean.TRUE.equals(row.getIsDeleted());
    }
}
