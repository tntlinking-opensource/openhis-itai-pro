/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.platform.identity;

import com.openhis.health.infrastructure.platform.organization.OrganizationUnitLinkSyncService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.whale.identity.application.OrganizationUnitAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 将用户表单维护的出诊科室（及默认登录科室）同步至 {@code whale_user_organization_units}，供登录选科。
 *
 * <p>口径：能登哪些科以用户里配置的出诊科室为准；不维护则登录不出现选科（医院级）。
 * 默认登录科室只决定登录后落到哪一科，不单独灌满/清空绑定。无账号级特例。</p>
 */
@Service
@RequiredArgsConstructor
public class UserLoginOrganizationSyncService {

    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final OrganizationUnitAppService organizationUnitAppService;
    private final OrganizationUnitLinkSyncService organizationUnitLinkSyncService;

    /**
     * 按医务人员有效岗位分配，刷新其平台用户的可登录科室。
     *
     * @param practitionerId 医务人员主键
     */
    public void syncForPractitioner(Long practitionerId) {
        if (practitionerId == null) {
            return;
        }
        Practitioner practitioner = practitionerQueryService.findById(practitionerId);
        if (practitioner == null || practitioner.getUserId() == null) {
            return;
        }
        this.syncLoginOrganizationsForUser(practitioner.getUserId(), practitioner);
    }

    /**
     * 按平台用户反查医务人员并刷新可登录科室。
     *
     * <p>无医务人员主档时不改绑定表（避免把已有配置清空）。</p>
     *
     * @param userId Whale 用户主键
     */
    public void syncForUserId(Long userId) {
        if (userId == null) {
            return;
        }
        Practitioner practitioner = practitionerQueryService.findByUserId(userId);
        if (practitioner == null) {
            return;
        }
        this.syncLoginOrganizationsForUser(userId, practitioner);
    }

    /**
     * 将用户可登录科室与出诊岗位 + 默认登录科室对齐并写入 Whale OU 绑定。
     *
     * @param userId       平台用户主键
     * @param practitioner 医务人员主档
     */
    private void syncLoginOrganizationsForUser(Long userId, Practitioner practitioner) {
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        // 出诊科室（岗位 org_id）= 登录可选/可切换科室来源
        orgIds.addAll(practitionerRoleQueryService.collectLoginOrganizationUnitIds(practitioner.getId()));
        // 默认登录科室：有则并入可选列表，供登录后落到该科
        if (practitioner.getOrgId() != null) {
            orgIds.add(practitioner.getOrgId());
        }
        // 二者皆空 → 同步为空列表（医院级，不出现选科）
        List<Long> whaleIds = organizationUnitLinkSyncService.resolveWhaleOrganizationUnitIds(
            new ArrayList<>(orgIds));
        organizationUnitAppService.syncOrganizationUnitsForUser(userId, whaleIds);
    }
}
