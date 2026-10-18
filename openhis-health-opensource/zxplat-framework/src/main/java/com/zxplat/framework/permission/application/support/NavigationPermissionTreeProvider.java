/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.permission.application.support;

import com.zxplat.framework.permission.application.dto.PermissionDto;

import java.util.List;

/**
 * 按 {@code zxplat_menus} 层级构建与菜单管理一致的权限树（角色授权、权限管理共用）。
 */
public interface NavigationPermissionTreeProvider {

    /**
     * 返回菜单同构的权限树；目录节点 {@link PermissionDto#getGrantable()} 为 {@code false}。
     */
    List<PermissionDto> buildMenuAlignedTree();
}
