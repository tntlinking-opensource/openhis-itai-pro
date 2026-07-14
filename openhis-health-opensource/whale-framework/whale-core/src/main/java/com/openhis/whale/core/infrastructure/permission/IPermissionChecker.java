/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.permission;

import java.util.Set;

/**
 * 权限检查器接口
 */
public interface IPermissionChecker {

    /**
     * 检查当前用户是否拥有指定权限
     * @param permissionName 权限名称
     * @return true表示拥有权限
     */
    boolean isGranted(String permissionName);

    /**
     * 检查指定用户是否拥有指定权限
     * @param userId 用户ID
     * @param permissionName 权限名称
     * @return true表示拥有权限
     */
    boolean isGranted(Long userId, String permissionName);

    /**
     * 批量解析指定用户在当前租户下有效的已授予权限名（用户直授 + 角色授权，去重）。
     */
    Set<String> getGrantedPermissionNames(Long userId);
}
