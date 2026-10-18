/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.practitioner;

import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.zxplat.framework.identity.application.UserAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 岗位分配与平台登录角色双向同步：{@code adm_pr_assign} ↔ {@code zxplat_user_roles}。
 *
 * <p>新增/启用岗位 → 追加登录角色；废弃岗位且该角色下已无激活行 → 撤销登录角色。
 * 用户维护 {@code saveStaff} 以 {@code roleIds} 为真源全量对齐两边。</p>
 */
@Component
@RequiredArgsConstructor
public class PositionUserRoleSyncService {

    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final UserAppService userAppService;

    /**
     * 新增或重新启用岗位后：追加登录角色（已存在则跳过）。
     *
     * @param practitionerId 医务人员主键
     * @param roleId         平台角色主键
     */
    public void onAssignmentAdded(Long practitionerId, Long roleId) {
        Long userId = this.resolveUserId(practitionerId);
        // Gate：未绑用户或角色空则无法写 zxplat_user_roles
        if (userId == null || roleId == null) {
            return;
        }
        // Cmd：幂等追加登录角色
        userAppService.addRoleIfAbsent(userId, roleId);
    }

    /**
     * 废弃岗位后：若该人员在此角色下已无激活岗位，则撤销对应登录角色。
     *
     * <p>仍有其它激活行（含双空挂靠）时保留 {@code zxplat_user_roles}，避免误摘登录权限。</p>
     *
     * @param practitionerId 医务人员主键
     * @param roleId         平台角色主键
     */
    public void onAssignmentRemoved(Long practitionerId, Long roleId) {
        Long userId = this.resolveUserId(practitionerId);
        // Gate：未绑用户或角色空则跳过
        if (userId == null || roleId == null) {
            return;
        }
        // 业务分支：同角色下仍有激活岗位则保留登录角色
        if (practitionerRoleQueryService.hasActiveAssignmentForRole(practitionerId, roleId)) {
            return;
        }
        // Cmd：无剩余激活岗位 → 软删 zxplat_user_roles 对应行
        userAppService.removeRoleIfPresent(userId, roleId);
    }

    /**
     * 全量替换某平台角色块后：有激活岗位则补登录角色，否则撤登录角色。
     *
     * @param practitionerId 医务人员主键
     * @param roleId         平台角色主键
     */
    public void afterReplaceBlock(Long practitionerId, Long roleId) {
        // 业务分支：替换后是否仍有激活行决定增/删登录角色
        if (practitionerRoleQueryService.hasActiveAssignmentForRole(practitionerId, roleId)) {
            this.onAssignmentAdded(practitionerId, roleId);
        } else {
            this.onAssignmentRemoved(practitionerId, roleId);
        }
    }

    /**
     * 医务人员 id → 绑定用户 id；不存在或未绑定时 null。
     *
     * @param practitionerId 医务人员主键
     * @return 用户 id 或 null
     */
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
