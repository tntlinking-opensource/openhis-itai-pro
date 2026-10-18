/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.practitioner;

import com.zxplat.domain.administration.practitionerrole.service.PractitionerRoleDomainService;
import com.zxplat.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.zxplat.domain.share.events.administration.practitioner.PractitionerRetiredEvent;
import com.zxplat.domain.infrastructure.platform.identity.UserLoginOrganizationSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 医务人员注销后联动废弃岗位分配，并同步 {@code zxplat_user_roles} / 登录科室。
 *
 * <p>监听 {@link PractitionerRetiredEvent}（含用户软删经 {@link UserPractitionerLifecycleSyncListener} 触发的路径）。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerRoleLifecycleSyncListener {

    private final PractitionerRoleDomainService practitionerRoleDomainService;
    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final PositionUserRoleSyncService positionUserRoleSyncService;
    private final UserLoginOrganizationSyncService userLoginOrganizationSyncService;

    /**
     * 医务人员注销后：软删其全部未删 {@code adm_pr_assign}，并按角色撤登录绑定、刷新登录科室。
     *
     * @param event 医务人员注销事件
     */
    @EventListener
    @Transactional
    public void onPractitionerRetired(PractitionerRetiredEvent event) {
        // Gate：事件或聚合主键缺失则跳过
        if (event == null || event.getAggregateId() == null) {
            return;
        }
        Long practitionerId = event.getAggregateId();
        // Cmd：废弃该人全部未删岗位
        List<Long> retiredAssignmentIds =
            this.practitionerRoleDomainService.retireAllAssignmentsByPractitioner(
                practitionerId, event.getOperatorId());
        LinkedHashSet<Long> retiredRoleIds = new LinkedHashSet<>();
        for (Long assignmentId : retiredAssignmentIds) {
            Long roleId = this.practitionerRoleQueryService.findRoleIdByAssignmentId(assignmentId);
            if (roleId != null) {
                retiredRoleIds.add(roleId);
            }
        }
        // Cmd 续：无剩余激活岗位的角色 → 撤 zxplat_user_roles
        for (Long roleId : retiredRoleIds) {
            this.positionUserRoleSyncService.onAssignmentRemoved(practitionerId, roleId);
        }
        // Cmd 续：岗位清空后刷新可登录科室（通常清空 zxplat_user_organization_units）
        this.userLoginOrganizationSyncService.syncForPractitioner(practitionerId);
    }
}
