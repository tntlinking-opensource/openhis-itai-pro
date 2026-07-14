/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.platform.identity;

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
 * 医务人员岗位管辖范围：跨聚合读编排（B.6.4），岗位分配与 location 形态分类分步解析。
 *
 * <p>经 {@link PractitionerRoleQueryService} 读岗位分配，经 {@link LocationQueryService} 解析 location 形态与上级病区。</p>
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
        // Gate：无人员主键时返回空管辖范围
        if (practitionerId == null) {
            return scope;
        }
        // Pre：激活岗位分配（B.6.4 只读编排，不经 Repository 直查）
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
        // Pre：批量解析 location 形态（B.6.4 跨聚合 Location QS）
        Map<Long, String> formByLocationId = this.locationQueryService.mapFormEnumCodeByIds(locationIdsToResolve);
        for (Long locationId : locationIdsToResolve) {
            String formCode = formByLocationId.get(locationId);
            // 形态未知时仍计入 managedLocationIds，避免岗位绑定 location 被静默丢弃
            if (formCode == null) {
                locationIds.add(locationId);
                continue;
            }
            String trimmed = formCode.trim();
            if (LocationForm.WARD.getCode().equals(trimmed)) {
                // 病区岗位：写入 managedWardLocationIds 供护士站树收窄
                wardLocationIds.add(locationId);
            } else if (LocationForm.HOUSE.getCode().equals(trimmed)) {
                // 病房岗位：回溯上级病区 id
                this.appendParentWardIdIfPresent(locationId, wardLocationIds);
            } else if (LocationForm.WAREHOUSE.getCode().equals(trimmed)
                || LocationForm.PHARMACY.getCode().equals(trimmed)
                || LocationForm.DEPARTMENT_PHARMACY.getCode().equals(trimmed)) {
                // 药库/药房/科室药房均计入 managedLocationIds（库存与发药 Gate 同源）
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
