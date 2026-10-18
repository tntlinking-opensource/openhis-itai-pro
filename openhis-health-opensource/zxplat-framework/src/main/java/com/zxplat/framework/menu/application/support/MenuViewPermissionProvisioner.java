/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.menu.application.support;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.infrastructure.permission.IPermissionGrantService;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import com.zxplat.framework.menu.domain.Menu;
import com.zxplat.framework.permission.domain.Permission;
import com.zxplat.framework.permission.infrastructure.repository.PermissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 菜单与 Navigation 权限自动同步：权限与可见页面一一对应。
 *
 * <p>目录不生成权限；隐藏/停用/删除的菜单撤销对应 Navigation 权限、清空角色/用户授权并清空 {@link Menu#getPermissionName()}；
 * 重新显示且启用后恢复权限定义与绑定。</p>
 */
@Component
public class MenuViewPermissionProvisioner {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private ICurrentUser currentUser;

    @Autowired
    private IPermissionGrantService permissionGrantService;

    /**
     * 按菜单状态同步 Navigation 权限（忽略请求体中的 permissionName）。
     */
    public void apply(Menu menu) {
        if (menu == null) {
            return;
        }
        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            revokeNavigationPermission(menu);
            menu.setPermissionName(null);
            return;
        }
        if (!MenuGrantability.isSyncableMenuEntry(menu)) {
            menu.setPermissionName(null);
            return;
        }
        if (menu.getCode() == null || menu.getCode().isBlank()) {
            throw new ValidationException("菜单编码不能为空");
        }
        if (!MenuGrantability.isGrantableMenuEntry(menu)) {
            revokeNavigationPermission(menu);
            menu.setPermissionName(null);
            return;
        }
        String permissionName = resolvePermissionName(menu);
        ensureNavigationRootPermission();
        upsertViewPermission(permissionName, menu);
        menu.setPermissionName(permissionName);
    }

    /**
     * 已有 permission_name 时保持不变，避免改 code 导致角色授权失效。
     */
    private String resolvePermissionName(Menu menu) {
        String existing = menu.getPermissionName();
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return MenuNavigationPermissionNames.menuPermissionName(menu.getCode());
    }

    private void ensureNavigationRootPermission() {
        String rootName = MenuNavigationPermissionNames.ROOT;
        if (permissionRepository.existsByName(rootName)) {
            return;
        }
        Permission root = new Permission();
        root.setName(rootName);
        root.setDisplayName("导航菜单");
        root.setDescription("由业务菜单自动生成的入口权限分组");
        root.setParentName(null);
        root.setTenantId(TenantUtils.getCurrentTenantId());
        permissionRepository.save(root);
    }

    private void upsertViewPermission(String permissionName, Menu menu) {
        Permission permission = permissionRepository.findByName(permissionName).orElse(null);
        String displayName = buildPermissionDisplayName(menu);
        if (permission == null) {
            permission = new Permission();
            permission.setName(permissionName);
            permission.setDisplayName(displayName);
            permission.setParentName(MenuNavigationPermissionNames.ROOT);
            permission.setDescription("菜单入口: " + menu.getCode());
            permission.setTenantId(TenantUtils.getCurrentTenantId());
            permissionRepository.save(permission);
            return;
        }
        boolean changed = false;
        if (Boolean.TRUE.equals(permission.getIsDeleted())) {
            permission.setIsDeleted(false);
            permission.setDeletedAt(null);
            permission.setDeletedBy(null);
            changed = true;
        }
        if (displayName != null && !displayName.equals(permission.getDisplayName())) {
            permission.setDisplayName(displayName);
            changed = true;
        }
        if (changed) {
            permissionRepository.save(permission);
        }
    }

    private void revokeNavigationPermission(Menu menu) {
        String permissionName = resolveExistingPermissionName(menu);
        if (permissionName == null) {
            return;
        }
        permissionGrantService.revokeAllGrantsForPermission(permissionName);
        permissionRepository.findByName(permissionName).ifPresent(permission -> {
            if (Boolean.TRUE.equals(permission.getIsDeleted())) {
                return;
            }
            permission.setIsDeleted(true);
            permission.setDeletedAt(LocalDateTime.now());
            Long userId = currentUser.getCurrentUserId();
            if (userId != null) {
                permission.setDeletedBy(userId);
            }
            permissionRepository.save(permission);
        });
    }

    private String resolveExistingPermissionName(Menu menu) {
        String permissionName = menu.getPermissionName();
        if (permissionName != null && !permissionName.isBlank()) {
            return permissionName.trim();
        }
        if (menu.getCode() == null || menu.getCode().isBlank()) {
            return null;
        }
        return MenuNavigationPermissionNames.menuPermissionName(menu.getCode());
    }

    private String buildPermissionDisplayName(Menu menu) {
        if (menu.getDisplayName() != null && !menu.getDisplayName().isBlank()) {
            return menu.getDisplayName().trim();
        }
        if (menu.getName() != null && !menu.getName().isBlank()) {
            return menu.getName().trim();
        }
        return menu.getCode().trim();
    }
}
