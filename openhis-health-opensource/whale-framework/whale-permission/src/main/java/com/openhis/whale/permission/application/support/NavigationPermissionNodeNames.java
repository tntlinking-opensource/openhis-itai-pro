/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.application.support;

/**
 * 导航类权限命名约定（与菜单 {@code Navigation.{Code}} 及目录分组节点一致）。
 */
public final class NavigationPermissionNodeNames {

    public static final String ROOT = "Navigation";

    private static final String FOLDER_PREFIX = ROOT + ".__folder__.";

    private NavigationPermissionNodeNames() {
    }

    public static String folderNodeName(Long menuId) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单主键不能为空");
        }
        return FOLDER_PREFIX + menuId;
    }

    public static boolean isFolderNodeName(String permissionName) {
        return permissionName != null && permissionName.startsWith(FOLDER_PREFIX);
    }

    public static boolean isGrantablePermissionName(String permissionName) {
        return permissionName != null
            && !permissionName.isBlank()
            && !isFolderNodeName(permissionName);
    }
}
