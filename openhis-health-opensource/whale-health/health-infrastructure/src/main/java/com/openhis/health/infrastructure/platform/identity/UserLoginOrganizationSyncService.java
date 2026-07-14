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
 * 将岗位派生科室同步至 {@code whale_user_organization_units}，供登录选科室使用。
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
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>(
            practitionerRoleQueryService.collectLoginOrganizationUnitIds(practitionerId));
        if (practitioner.getOrgId() != null) {
            orgIds.add(practitioner.getOrgId());
        }
        List<Long> whaleIds = organizationUnitLinkSyncService.resolveWhaleOrganizationUnitIds(
            new ArrayList<>(orgIds));
        organizationUnitAppService.syncOrganizationUnitsForUser(practitioner.getUserId(), whaleIds);
    }

    /**
     * 按平台用户反查医务人员并刷新可登录科室。
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
        syncForPractitioner(practitioner.getId());
    }
}
