/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.querysupport.administration;

import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerManagedScopeQueryModel;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerRoleAssignmentQueryModel;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.health.domain.share.enums.LocationForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 医务人员岗位管辖范围：跨聚合读编排（B.6.1.3），岗位分配与 location 形态分类分步解析。
 */
@Component
@RequiredArgsConstructor
public class PractitionerManagedScopeLoader {

    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final LocationQueryService locationQueryService;

    /**
     * 从激活岗位分配派生数据管辖：科室看 org_id；库房/药房/病区看 location 形态。
     *
     * @param practitionerId 医务人员主键
     * @return 管辖范围模型
     */
    public PractitionerManagedScopeQueryModel resolveManagedScope(Long practitionerId) {
        PractitionerManagedScopeQueryModel scope = new PractitionerManagedScopeQueryModel();
        if (practitionerId == null) {
            return scope;
        }
        List<PractitionerRoleAssignmentQueryModel> assignments =
            this.practitionerRoleQueryService.listAssignmentsByPractitioner(practitionerId);
        if (assignments.isEmpty()) {
            return scope;
        }
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> wardLocationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIdsToResolve = new LinkedHashSet<>();
        for (PractitionerRoleAssignmentQueryModel row : assignments) {
            if (row == null) {
                continue;
            }
            if (row.getOrgId() != null) {
                organizationIds.add(row.getOrgId());
            }
            if (row.getLocationId() != null) {
                locationIdsToResolve.add(row.getLocationId());
            }
        }
        Map<Long, String> formByLocationId = this.locationQueryService.mapFormEnumCodeByIds(locationIdsToResolve);
        for (Long locationId : locationIdsToResolve) {
            String formCode = formByLocationId.get(locationId);
            if (formCode == null) {
                locationIds.add(locationId);
                continue;
            }
            String trimmed = formCode.trim();
            if (LocationForm.WARD.getCode().equals(trimmed)) {
                wardLocationIds.add(locationId);
            } else if (LocationForm.HOUSE.getCode().equals(trimmed)) {
                this.appendParentWardIdIfPresent(locationId, wardLocationIds);
            } else if (LocationForm.WAREHOUSE.getCode().equals(trimmed)
                || LocationForm.PHARMACY.getCode().equals(trimmed)) {
                locationIds.add(locationId);
            } else {
                locationIds.add(locationId);
            }
        }
        scope.setManagedLocationIds(new ArrayList<>(locationIds));
        scope.setManagedOrganizationUnitIds(new ArrayList<>(organizationIds));
        scope.setManagedWardLocationIds(new ArrayList<>(wardLocationIds));
        return scope;
    }

    /** 护士岗位绑定病房时，将上级病区写入 {@code managedWardLocationIds}。 */
    private void appendParentWardIdIfPresent(Long houseLocationId, LinkedHashSet<Long> wardLocationIds) {
        this.locationQueryService.findOptionalById(houseLocationId).ifPresent(house -> {
            Long parentId = house.getParentId();
            if (parentId == null) {
                return;
            }
            this.locationQueryService.findOptionalById(parentId).ifPresent(parent -> {
                if (LocationForm.WARD.getCode().equals(parent.getFormEnum()) && parent.getId() != null) {
                    wardLocationIds.add(parent.getId());
                }
            });
        });
    }
}
