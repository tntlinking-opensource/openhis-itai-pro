/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.support;

import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.identity.application.UserAppService;
import com.openhis.whale.identity.application.dto.RoleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存/效期预警可见性：仅库管或药师登录角色可查看工作台预警与统计。
 */
@Component
@RequiredArgsConstructor
public class InventoryWarningRoleGate {

    private final ICurrentUser currentUser;
    private final UserAppService userAppService;

    /**
     * 当前登录用户是否具备库存预警查看权限（{@code ROLE_WAREHOUSE} 或 {@code ROLE_PHARMACIST}）。
     *
     * @return 可查看时为 {@code true}
     */
    public boolean canViewInventoryWarnings() {
        // 未登录用户不可见预警
        if (!this.currentUser.isAuthenticated()) {
            return false;
        }
        Long userId = this.currentUser.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        List<RoleDto> roles = this.userAppService.getRoles(userId);
        for (RoleDto role : roles) {
            if (role == null || role.getCode() == null) {
                continue;
            }
            String code = role.getCode().trim();
            // 库管或药师角色可查看预警入口
            if (WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE.equalsIgnoreCase(code)
                || WhalePlatformRoleCodeConstants.ROLE_PHARMACIST.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}
