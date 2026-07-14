/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.menu.application.support;

/**
 * 由菜单自动生成的导航类权限命名约定。
 */
public final class MenuNavigationPermissionNames {

    /** 导航菜单权限分组（挂到权限树根下）。 */
    public static final String ROOT = "Navigation";

    private MenuNavigationPermissionNames() {
    }

    /**
     * 根据菜单 code 生成菜单级权限名（当前表示该菜单全部能力），例如 {@code user_management} → {@code Navigation.UserManagement}。
     */
    public static String menuPermissionName(String menuCode) {
        return ROOT + "." + toPermissionSegment(menuCode);
    }

    static String toPermissionSegment(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("菜单编码不能为空");
        }
        String trimmed = code.trim();
        StringBuilder segment = new StringBuilder();
        for (String part : trimmed.split("[_\\-.]+")) {
            if (part.isEmpty()) {
                continue;
            }
            segment.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                segment.append(part.substring(1).toLowerCase());
            }
        }
        if (segment.isEmpty()) {
            throw new IllegalArgumentException("菜单编码无效: " + code);
        }
        return segment.toString();
    }
}
