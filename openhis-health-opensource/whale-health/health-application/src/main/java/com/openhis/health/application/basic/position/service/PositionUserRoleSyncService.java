/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.application.basic.position.service;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.whale.identity.application.UserAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 岗位分配与登录角色同步：岗位增删驱动 {@code whale_user_roles} 增量变更。
 */
@Component
@RequiredArgsConstructor
public class PositionUserRoleSyncService {

    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final UserAppService userAppService;

    /**
     * 新增岗位后：追加登录角色（已存在则跳过）。
     */
    public void onAssignmentAdded(Long practitionerId, Long roleId) {
        Long userId = resolveUserId(practitionerId);
        if (userId == null || roleId == null) {
            return;
        }
        userAppService.addRoleIfAbsent(userId, roleId);
    }

    /**
     * 岗位撤销后不同步移除登录角色：平台角色由用户维护界面的 {@code assignRoles} 统一管理。
     */
    public void onAssignmentRemoved(Long practitionerId, Long roleId) {
        // no-op: adm_pr_assign 与 whale_user_roles 解耦，避免 saveStaff 空岗位块清掉已选角色
    }

    /**
     * 全量替换某平台角色块后：确保登录角色与有效岗位一致。
     */
    public void afterReplaceBlock(Long practitionerId, Long roleId) {
        if (practitionerRoleQueryService.hasActiveAssignmentForRole(practitionerId, roleId)) {
            onAssignmentAdded(practitionerId, roleId);
        } else {
            onAssignmentRemoved(practitionerId, roleId);
        }
    }

    private Long resolveUserId(Long practitionerId) {
        if (practitionerId == null) {
            return null;
        }
        Practitioner practitioner = practitionerQueryService.findById(practitionerId);
        if (practitioner == null) {
            return null;
        }
        return practitioner.getUserId();
    }
}
