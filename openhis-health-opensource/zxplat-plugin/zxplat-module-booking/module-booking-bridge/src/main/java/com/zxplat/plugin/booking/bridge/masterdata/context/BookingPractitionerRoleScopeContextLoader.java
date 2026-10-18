/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.masterdata.context;

import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** 岗位分配 → 医务人员预解析（bridge 内聚，替代 health-module-support）。 */
@Component
@RequiredArgsConstructor
public class BookingPractitionerRoleScopeContextLoader {

    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final BookingPlatformRoleLookup platformRoleLookup;

    public List<Practitioner> listActivePractitionersForOutpatientRegistration(String platformRoleCode, Long orgId) {
        Long platformRoleId = platformRoleLookup.findRoleIdByCode(platformRoleCode);
        if (platformRoleId == null) {
            return Collections.emptyList();
        }
        Set<Long> resolvedIds;
        if (orgId != null) {
            resolvedIds = practitionerRoleQueryService.listPractitionerIdsWithActiveRoleId(platformRoleId, orgId);
        } else {
            List<Organization> outpatientOrganizations =
                    organizationQueryService.listOrganizationsForOutpatientRegistration();
            List<Long> outpatientOrganizationIds = new ArrayList<>();
            for (Organization organization : outpatientOrganizations) {
                if (organization != null && organization.getId() != null) {
                    outpatientOrganizationIds.add(organization.getId());
                }
            }
            if (outpatientOrganizationIds.isEmpty()) {
                return Collections.emptyList();
            }
            resolvedIds = practitionerRoleQueryService.listPractitionerIdsWithActiveRoleIdInOrganizations(
                    platformRoleId, outpatientOrganizationIds);
        }
        if (resolvedIds.isEmpty()) {
            return Collections.emptyList();
        }
        return practitionerQueryService.listActivePractitionersByResolvedRoleScope(resolvedIds);
    }
}
