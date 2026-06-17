/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.domain.administration.practitionerrole.service;

import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRole;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRoleServiceItem;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerHealthcareServiceQueryModel;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerOrgIdQueryModel;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerRoleAssignmentQueryModel;
import com.openhis.health.domain.administration.practitionerrole.repository.PractitionerRoleRepository;
import com.openhis.health.domain.administration.practitionerrole.repository.PractitionerRoleServiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 岗位分配读侧查询服务（{@code adm_pr_assign}）。
 */
@Service
@RequiredArgsConstructor
public class PractitionerRoleQueryService {

    private final PractitionerRoleRepository repository;
    private final PractitionerRoleServiceItemRepository serviceItemRepository;

    public List<PractitionerRoleAssignmentQueryModel> listAssignmentsByPractitioner(Long practitionerId) {
        List<PractitionerRole> roles = repository.findAllByPractitionerIdAndActiveFlagTrue(practitionerId);
        if (roles.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> assignmentIds = new ArrayList<>();
        for (PractitionerRole role : roles) {
            assignmentIds.add(role.getId());
        }

        List<PractitionerRoleServiceItem> serviceItems = serviceItemRepository.findAllByAssignmentIdIn(assignmentIds);
        Map<Long, List<Long>> serviceIdsByAssignmentId = new LinkedHashMap<>();
        for (PractitionerRoleServiceItem row : serviceItems) {
            List<Long> list = serviceIdsByAssignmentId.get(row.getAssignmentId());
            if (list == null) {
                list = new ArrayList<>();
                serviceIdsByAssignmentId.put(row.getAssignmentId(), list);
            }
            list.add(row.getHealthcareServiceId());
        }

        List<PractitionerRoleAssignmentQueryModel> result = new ArrayList<>();
        for (PractitionerRole role : roles) {
            PractitionerRoleAssignmentQueryModel model = new PractitionerRoleAssignmentQueryModel();
            model.setAssignmentId(role.getId());
            model.setRoleId(role.getRoleId());
            model.setOrgId(role.getOrgId());
            model.setLocationId(role.getLocationId());
            model.setServiceIds(serviceIdsByAssignmentId.get(role.getId()));
            result.add(model);
        }
        return result;
    }

    /**
     * 拥有指定平台角色且岗位仍激活的医务人员主键集合。
     *
     * @param roleId 平台角色主键
     * @param orgId  科室主键；{@code null} 时不按 {@code org_id} 收窄
     */
    public Set<Long> listPractitionerIdsWithActiveRoleId(Long roleId, Long orgId) {
        if (roleId == null) {
            return Collections.emptySet();
        }
        List<PractitionerRole> rows;
        if (orgId != null) {
            rows = repository.findAllByRoleIdAndOrgIdAndActiveFlagTrue(roleId, orgId);
        } else {
            rows = repository.findAllByRoleIdAndActiveFlagTrue(roleId);
        }
        return this.extractActivePractitionerIds(rows);
    }

    /**
     * 拥有指定平台角色、岗位仍激活且挂在给定病区位置的医务人员主键集合。
     *
     * @param roleId     平台角色主键
     * @param locationId 病区位置主键（{@code form_enum=WARD}）
     * @return 医务人员主键集合
     */
    public Set<Long> listPractitionerIdsWithActiveRoleIdAtLocation(Long roleId, Long locationId) {
        if (roleId == null || locationId == null) {
            return Collections.emptySet();
        }
        List<PractitionerRole> rows =
            repository.findAllByRoleIdAndLocationIdAndActiveFlagTrue(roleId, locationId);
        return this.extractActivePractitionerIds(rows);
    }

    /**
     * 拥有指定平台角色、岗位仍激活且机构落在给定集合内的医务人员主键集合。
     *
     * <p>供门诊挂号等场景：未指定科室时仍须限定在门诊科室范围内有医师岗，排除仅挂住院科室岗的医师。</p>
     *
     * @param roleId 平台角色主键
     * @param orgIds 机构主键集合；空时返回空集合
     * @return 医务人员主键集合
     */
    public Set<Long> listPractitionerIdsWithActiveRoleIdInOrganizations(Long roleId, Collection<Long> orgIds) {
        if (roleId == null || orgIds == null || orgIds.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<Long> distinctOrgIds = new LinkedHashSet<>();
        for (Long orgId : orgIds) {
            if (orgId != null) {
                distinctOrgIds.add(orgId);
            }
        }
        if (distinctOrgIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<PractitionerRole> rows =
            repository.findAllByRoleIdAndOrgIdInAndActiveFlagTrue(roleId, distinctOrgIds);
        return this.extractActivePractitionerIds(rows);
    }

    /**
     * 拥有指定平台角色、岗位仍激活且挂在给定药房/库位上的医务人员主键集合。
     *
     * <p>发药调配药师候选：药师岗 {@code adm_pr_assign.location_id} 须落在目标药房 Location 集合内。</p>
     *
     * @param roleId      平台角色主键
     * @param locationIds 药房等位置主键集合；空时返回空集合
     * @return 医务人员主键集合
     */
    public Set<Long> listPractitionerIdsWithActiveRoleIdInLocations(Long roleId, Collection<Long> locationIds) {
        if (roleId == null || locationIds == null || locationIds.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<Long> distinctLocationIds = new LinkedHashSet<>();
        for (Long locationId : locationIds) {
            if (locationId != null) {
                distinctLocationIds.add(locationId);
            }
        }
        if (distinctLocationIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<PractitionerRole> rows =
            repository.findAllByRoleIdAndLocationIdInAndActiveFlagTrue(roleId, distinctLocationIds);
        return this.extractActivePractitionerIds(rows);
    }

    public boolean hasActiveAssignmentForRole(Long practitionerId, Long roleId) {
        if (practitionerId == null || roleId == null) {
            return false;
        }
        return repository.countByPractitionerIdAndRoleIdAndActiveFlagTrueAndIsDeletedFalse(practitionerId, roleId) > 0;
    }

    public Long findRoleIdByAssignmentId(Long assignmentId) {
        if (assignmentId == null) {
            return null;
        }
        return repository.findById(assignmentId).map(PractitionerRole::getRoleId).orElse(null);
    }

    public List<PractitionerOrgIdQueryModel> listOrganizationIdsByPractitioner(Long practitionerId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (PractitionerRoleAssignmentQueryModel row : this.listAssignmentsByPractitioner(practitionerId)) {
            if (row.getOrgId() != null) {
                ids.add(row.getOrgId());
            }
        }
        List<PractitionerOrgIdQueryModel> result = new ArrayList<>();
        for (Long orgId : ids) {
            PractitionerOrgIdQueryModel model = new PractitionerOrgIdQueryModel();
            model.setPractitionerId(practitionerId);
            model.setOrgId(orgId);
            result.add(model);
        }
        return result;
    }

    public List<PractitionerHealthcareServiceQueryModel> listHealthcareServiceIdsByPractitionerAndOrg(
        Long practitionerId,
        Long orgId) {
        List<PractitionerRole> roles = repository.findAllByPractitionerIdAndOrgIdAndActiveFlagTrue(practitionerId, orgId);
        if (roles.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> assignmentIds = new ArrayList<>();
        for (PractitionerRole role : roles) {
            assignmentIds.add(role.getId());
        }

        List<PractitionerRoleServiceItem> serviceItems = serviceItemRepository.findAllByAssignmentIdIn(assignmentIds);
        List<PractitionerHealthcareServiceQueryModel> result = new ArrayList<>();
        for (PractitionerRoleServiceItem row : serviceItems) {
            PractitionerHealthcareServiceQueryModel model = new PractitionerHealthcareServiceQueryModel();
            model.setAssignmentId(row.getAssignmentId());
            model.setOrgId(orgId);
            model.setHealthcareServiceId(row.getHealthcareServiceId());
            result.add(model);
        }
        return result;
    }

    public List<PractitionerRole> listAllRolesByPractitioner(Long practitionerId) {
        if (practitionerId == null) {
            return Collections.emptyList();
        }
        return repository.findAllByPractitionerId(practitionerId);
    }

    /**
     * 收集登录可选科室主键（仅 org_id，不含 location 形态分类）。
     */
    public List<Long> collectLoginOrganizationUnitIds(Long practitionerId) {
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        for (PractitionerRoleAssignmentQueryModel row : this.listAssignmentsByPractitioner(practitionerId)) {
            if (row != null && row.getOrgId() != null) {
                orgIds.add(row.getOrgId());
            }
        }
        return new ArrayList<>(orgIds);
    }

    public Long findPractitionerIdByAssignmentId(Long assignmentId) {
        if (assignmentId == null) {
            return null;
        }
        return repository.findById(assignmentId)
            .map(PractitionerRole::getPractitionerId)
            .orElse(null);
    }

    private Set<Long> extractActivePractitionerIds(List<PractitionerRole> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (PractitionerRole row : rows) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (row.getPractitionerId() != null) {
                ids.add(row.getPractitionerId());
            }
        }
        return ids;
    }
}
