/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.infrastructure;

import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.infrastructure.permission.IPermissionChecker;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.identity.domain.UserRole;
import com.openhis.whale.identity.infrastructure.repository.UserRoleRepository;
import com.openhis.whale.permission.domain.Permission;
import com.openhis.whale.permission.domain.PermissionGrant;
import com.openhis.whale.permission.infrastructure.repository.PermissionGrantRepository;
import com.openhis.whale.permission.infrastructure.repository.PermissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限检查器实现
 */
@Component
public class PermissionChecker implements IPermissionChecker {

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private ICurrentUser currentUser;

    @Override
    public boolean isGranted(String permissionName) {
        if (!currentUser.isAuthenticated()) {
            return false;
        }

        Long userId = currentUser.getCurrentUserId();
        if (userId == null) {
            return false;
        }

        return isGranted(userId, permissionName);
    }

    @Override
    public boolean isGranted(Long userId, String permissionName) {
        if (permissionName == null || permissionName.isBlank()) {
            return false;
        }
        return getGrantedPermissionNames(userId).contains(permissionName.trim());
    }

    @Override
    public Set<String> getGrantedPermissionNames(Long userId) {
        if (userId == null) {
            return Set.of();
        }

        Long tenantId = TenantUtils.getCurrentTenantId();
        Set<String> granted = new HashSet<>();
        Set<String> activePermissionNames = loadActivePermissionNames();

        List<PermissionGrant> userGrants =
            permissionGrantRepository.findByProviderNameAndProviderKey("U", String.valueOf(userId));
        collectGrantedNames(granted, userGrants, tenantId, activePermissionNames);

        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        List<String> roleIds = userRoles.stream()
            .filter(ur -> !Boolean.TRUE.equals(ur.getIsDeleted()))
            .map(UserRole::getRoleId)
            // 跳过 role_id 为空的脏关联行，避免角色授权查询 providerKey 异常
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .collect(Collectors.toList());

        if (!roleIds.isEmpty()) {
            List<PermissionGrant> roleGrants =
                permissionGrantRepository.findByProviderNameAndProviderKeyIn("R", roleIds);
            collectGrantedNames(granted, roleGrants, tenantId, activePermissionNames);
        }

        return Collections.unmodifiableSet(granted);
    }

    private Set<String> loadActivePermissionNames() {
        Specification<Permission> spec = SpecificationUtils.<Permission>tenantFilter()
            .and(SpecificationUtils.<Permission>notDeleted());
        Set<String> names = new HashSet<>();
        for (Permission permission : permissionRepository.findAll(spec)) {
            if (permission.getName() != null && !permission.getName().isBlank()) {
                names.add(permission.getName().trim());
            }
        }
        return names;
    }

    private void collectGrantedNames(
        Set<String> target,
        List<PermissionGrant> grants,
        Long tenantId,
        Set<String> activePermissionNames) {
        if (grants == null || grants.isEmpty()) {
            return;
        }
        for (PermissionGrant grant : grants) {
            if (Boolean.TRUE.equals(grant.getIsDeleted())) {
                continue;
            }
            String permissionName = grant.getPermissionName();
            if (permissionName == null || permissionName.isBlank()) {
                continue;
            }
            String normalized = permissionName.trim();
            if (!activePermissionNames.contains(normalized)) {
                continue;
            }
            if (tenantId == null || grant.getTenantId() == null || grant.getTenantId().equals(tenantId)) {
                target.add(normalized);
            }
        }
    }
}
