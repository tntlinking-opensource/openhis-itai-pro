/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.menu.application.support;

import com.zxplat.framework.menu.domain.Menu;

/**
 * 菜单是否参与 Navigation 权限同步与角色授权树展示。
 */
public final class MenuGrantability {

    private static final String DIRECTORY_MENU_TYPE = "directory";

    private MenuGrantability() {
    }

    /** 非目录、未删除的菜单条目（可绑定 permission_name）。 */
    public static boolean isSyncableMenuEntry(Menu menu) {
        if (menu == null || Boolean.TRUE.equals(menu.getIsDeleted())) {
            return false;
        }
        return !isDirectory(menu);
    }

    /** 启用且未隐藏的菜单条目（可授权、可进权限树）。 */
    public static boolean isGrantableMenuEntry(Menu menu) {
        if (!isSyncableMenuEntry(menu)) {
            return false;
        }
        if (!Boolean.TRUE.equals(menu.getIsEnabled())) {
            return false;
        }
        return !Boolean.TRUE.equals(menu.getIsHidden());
    }

    private static boolean isDirectory(Menu menu) {
        String menuType = menu.getMenuType();
        return menuType != null && DIRECTORY_MENU_TYPE.equalsIgnoreCase(menuType.trim());
    }
}
