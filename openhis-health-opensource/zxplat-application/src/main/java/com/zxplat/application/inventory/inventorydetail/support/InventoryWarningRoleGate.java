/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.support;

import com.zxplat.framework.core.infrastructure.permission.IPermissionChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 库存/效期预警可见性：具备「库存明细」菜单权限（{@code Navigation.Invstocklist}）即可查看。
 */
@Component
@RequiredArgsConstructor
public class InventoryWarningRoleGate {

    /** 库存明细菜单导航权限名（与菜单 {@code InvStockList} / 路径 {@code /inventory/stock-list} 同源）。 */
    private static final String STOCK_LIST_MENU_PERMISSION = "Navigation.Invstocklist";

    private final IPermissionChecker permissionChecker;

    /**
     * 当前登录用户是否具备库存预警查看权限（与库存明细菜单权限一致）。
     *
     * @return 可查看时为 {@code true}
     */
    public boolean canViewInventoryWarnings() {
        // Gate：与库存明细菜单授权同源，无该菜单则不展示工作台预警入口
        return this.permissionChecker.isGranted(STOCK_LIST_MENU_PERMISSION);
    }
}
