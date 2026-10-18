/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderaudit.support;

import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitionerrole.queries.model.PractitionerRoleAssignmentQueryModel;
import com.zxplat.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校对人执行科室展示解析（B.6.4 跨聚合只读编排）。
 *
 * <p>护士岗位通常绑定病区 {@code location_id} 而非科室 {@code org_id}；本类按校对人岗位回溯病区，
 * 再取病区 {@code organization_id} 对应科室名称，供医嘱执行等列表回显「执行科室」。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerCheckOrgDisplayResolver {

    /** 校对人 + 就诊复合键分隔符（与列表行键同口径 ASCII SOH）。 */
    private static final char LOOKUP_KEY_SEPARATOR = '\u0001';

    /** 岗位分配读服务：取校对人激活岗位。 */
    private final PractitionerRoleQueryService practitionerRoleQueryService;

    /** 位置读服务：病区形态与上级科室解析。 */
    private final LocationQueryService locationQueryService;

    /** 机构读服务：科室展示名批量解析。 */
    private final OrganizationQueryService organizationQueryService;

    /** 就诊读服务：按就诊取活动病区以收窄多病区岗位。 */
    private final EncounterQueryService encounterQueryService;

    /**
     * 按药/诊明细批量预热「校对人 + 就诊 → 执行科室展示名」映射。
     *
     * @param medDetails 药品明细集合；可空
     * @param svcDetails 诊疗明细集合；可空
     * @return 复合键 → 科室展示名；无校对人或无法解析时该键不入表
     */
    public Map<String, String> buildPerformerCheckOrgDisplayByKey(
        Collection<MedicationRequestListQueryModel> medDetails,
        Collection<ServiceRequestListModel> svcDetails) {
        List<PerformerEncounterAnchor> anchors = new ArrayList<>();
        this.collectPerformerEncounterAnchors(medDetails, svcDetails, anchors);
        if (anchors.isEmpty()) {
            return new HashMap<>();
        }
        Set<Long> performerCheckIds = new LinkedHashSet<>();
        Set<Long> encounterIds = new LinkedHashSet<>();
        for (PerformerEncounterAnchor anchor : anchors) {
            practitionerCheckIdsAdd(performerCheckIds, anchor.performerCheckId);
            encounterIds.add(anchor.encounterId);
        }
        Map<Long, PractitionerWardScope> scopeByPractitioner =
            this.buildPractitionerWardScopeByPractitionerId(performerCheckIds);
        Map<Long, Long> encounterWardLocationIdByEncounterId =
            encounterQueryService.mapLastCompletedWardLocationIdByEncounterIds(encounterIds);
        Set<Long> orgIdsToResolve = new LinkedHashSet<>();
        Map<String, Long> orgIdByLookupKey = new LinkedHashMap<>();
        for (PerformerEncounterAnchor anchor : anchors) {
            Long orgId = this.resolveCheckOrgId(
                anchor.performerCheckId,
                anchor.encounterId,
                scopeByPractitioner.get(anchor.performerCheckId),
                encounterWardLocationIdByEncounterId);
            if (orgId == null) {
                continue;
            }
            orgIdsToResolve.add(orgId);
            orgIdByLookupKey.put(
                this.buildLookupKey(anchor.performerCheckId, anchor.encounterId),
                orgId);
        }
        if (orgIdByLookupKey.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, String> orgNameById = organizationQueryService.mapNamesByIds(orgIdsToResolve);
        Map<String, String> displayByKey = new HashMap<>(orgIdByLookupKey.size() * 2);
        for (Map.Entry<String, Long> entry : orgIdByLookupKey.entrySet()) {
            String orgName = orgNameById.get(entry.getValue());
            if (StringUtils.isNotEmpty(orgName)) {
                displayByKey.put(entry.getKey(), orgName);
            }
        }
        return displayByKey;
    }

    /** 去重写入校对人主键集合。 */
    private void practitionerCheckIdsAdd(Set<Long> practitionerCheckIds, Long performerCheckId) {
        if (performerCheckId != null) {
            practitionerCheckIds.add(performerCheckId);
        }
    }

    /**
     * 从预热映射中解析单行执行科室展示名。
     *
     * @param displayByKey     {@link #buildPerformerCheckOrgDisplayByKey} 产出
     * @param performerCheckId 校对人主键；空则 null
     * @param encounterId      就诊主键；空则 null
     * @return 科室展示名；未预热或无法解析时 null
     */
    public String resolvePerformerCheckOrgDisplay(
        Map<String, String> displayByKey,
        Long performerCheckId,
        Long encounterId) {
        if (displayByKey == null || displayByKey.isEmpty() || performerCheckId == null || encounterId == null) {
            return null;
        }
        return displayByKey.get(this.buildLookupKey(performerCheckId, encounterId));
    }

    /**
     * 扫描明细收集全部「校对人 + 就诊」锚点（去重）。
     *
     * @param medDetails 药品明细
     * @param svcDetails 诊疗明细
     * @param anchors    输出锚点列表
     */
    private void collectPerformerEncounterAnchors(
        Collection<MedicationRequestListQueryModel> medDetails,
        Collection<ServiceRequestListModel> svcDetails,
        List<PerformerEncounterAnchor> anchors) {
        Set<String> seenKeys = new HashSet<>();
        if (medDetails != null) {
            for (MedicationRequestListQueryModel row : medDetails) {
                this.appendPerformerEncounterAnchor(anchors, seenKeys, row.getPerformerCheckId(), row.getEncounterId());
            }
        }
        if (svcDetails != null) {
            for (ServiceRequestListModel row : svcDetails) {
                this.appendPerformerEncounterAnchor(anchors, seenKeys, row.getPerformerCheckId(), row.getEncounterId());
            }
        }
    }

    /** 追加去重后的校对人+就诊锚点。 */
    private void appendPerformerEncounterAnchor(
        List<PerformerEncounterAnchor> anchors,
        Set<String> seenKeys,
        Long performerCheckId,
        Long encounterId) {
        if (performerCheckId == null || encounterId == null) {
            return;
        }
        String key = this.buildLookupKey(performerCheckId, encounterId);
        if (!seenKeys.add(key)) {
            return;
        }
        PerformerEncounterAnchor anchor = new PerformerEncounterAnchor();
        anchor.performerCheckId = performerCheckId;
        anchor.encounterId = encounterId;
        anchors.add(anchor);
    }

    /**
     * 批量构建校对人岗位范围：病区 location 集合与直属 org 集合。
     *
     * @param performerCheckIds 校对人主键集合
     * @return 校对人 → 岗位范围
     */
    private Map<Long, PractitionerWardScope> buildPractitionerWardScopeByPractitionerId(
        Collection<Long> practitionerCheckIds) {
        Map<Long, PractitionerWardScope> scopeByPractitioner = new LinkedHashMap<>();
        Set<Long> allLocationIdsToResolve = new LinkedHashSet<>();
        for (Long practitionerId : practitionerCheckIds) {
            if (practitionerId == null) {
                continue;
            }
            PractitionerWardScope scope = new PractitionerWardScope();
            List<PractitionerRoleAssignmentQueryModel> assignments =
                practitionerRoleQueryService.listAssignmentsByPractitioner(practitionerId);
            for (PractitionerRoleAssignmentQueryModel assignment : assignments) {
                if (assignment == null) {
                    continue;
                }
                if (assignment.getOrgId() != null) {
                    scope.directOrgIds.add(assignment.getOrgId());
                }
                if (assignment.getLocationId() != null) {
                    allLocationIdsToResolve.add(assignment.getLocationId());
                    scope.boundLocationIds.add(assignment.getLocationId());
                }
            }
            scopeByPractitioner.put(practitionerId, scope);
        }
        if (allLocationIdsToResolve.isEmpty()) {
            return scopeByPractitioner;
        }
        Map<Long, String> formByLocationId = locationQueryService.mapFormEnumCodeByIds(allLocationIdsToResolve);
        Map<Long, Location> locationById = locationQueryService.loadLocationEntityMapByIds(allLocationIdsToResolve);
        for (PractitionerWardScope scope : scopeByPractitioner.values()) {
            LinkedHashSet<Long> wardLocationIds = new LinkedHashSet<>();
            for (Long locationId : scope.boundLocationIds) {
                String formCode = formByLocationId.get(locationId);
                if (formCode == null) {
                    continue;
                }
                String trimmed = formCode.trim();
                if (LocationForm.WARD.getCode().equals(trimmed)) {
                    wardLocationIds.add(locationId);
                } else if (LocationForm.HOUSE.getCode().equals(trimmed)) {
                    this.appendParentWardIdIfPresent(locationId, locationById, wardLocationIds);
                }
            }
            scope.wardLocationIds = wardLocationIds;
            // 病房回溯的病区可能不在首轮 locationById，补批量加载
            Map<Long, Location> wardLocationById =
                locationQueryService.loadLocationEntityMapByIds(wardLocationIds);
            for (Long wardLocationId : wardLocationIds) {
                Location ward = wardLocationById.get(wardLocationId);
                if (ward == null) {
                    ward = locationQueryService.findOptionalById(wardLocationId).orElse(null);
                }
                if (ward != null && ward.getOrganizationId() != null) {
                    scope.orgIdByWardLocationId.put(wardLocationId, ward.getOrganizationId());
                }
            }
        }
        return scopeByPractitioner;
    }

    /**
     * 解析单条医嘱行的校对科室主键：优先匹配就诊所在病区，其次取首个病区上级科室，最后回落直属 org 岗位。
     *
     * @param performerCheckId                   校对人
     * @param encounterId                        就诊
     * @param scope                              校对人岗位范围；可 null
     * @param encounterWardLocationIdByEncounterId 就诊 → 病区 location
     * @return 科室 org 主键；无法解析时 null
     */
    private Long resolveCheckOrgId(
        Long performerCheckId,
        Long encounterId,
        PractitionerWardScope scope,
        Map<Long, Long> encounterWardLocationIdByEncounterId) {
        if (scope == null) {
            return null;
        }
        Long encounterWardLocationId = null;
        if (encounterWardLocationIdByEncounterId != null) {
            encounterWardLocationId = encounterWardLocationIdByEncounterId.get(encounterId);
        }
        // 护士多病区岗位时，优先取与患者就诊病区一致的上级科室
        if (encounterWardLocationId != null && scope.orgIdByWardLocationId.containsKey(encounterWardLocationId)) {
            return scope.orgIdByWardLocationId.get(encounterWardLocationId);
        }
        for (Long wardLocationId : scope.wardLocationIds) {
            Long orgId = scope.orgIdByWardLocationId.get(wardLocationId);
            if (orgId != null) {
                return orgId;
            }
        }
        for (Long orgId : scope.directOrgIds) {
            if (orgId != null) {
                return orgId;
            }
        }
        return null;
    }

    /** 病房岗位回溯上级病区 id（与 {@code PractitionerManagedScopeLoader} 同口径）。 */
    private void appendParentWardIdIfPresent(
        Long houseLocationId,
        Map<Long, Location> locationById,
        Set<Long> wardLocationIds) {
        Location house = locationById.get(houseLocationId);
        if (house == null) {
            house = locationQueryService.findOptionalById(houseLocationId).orElse(null);
        }
        if (house == null || house.getParentId() == null) {
            return;
        }
        Location parent = locationById.get(house.getParentId());
        if (parent == null) {
            parent = locationQueryService.findOptionalById(house.getParentId()).orElse(null);
        }
        if (parent != null
            && LocationForm.WARD.getCode().equals(parent.getFormEnum())
            && parent.getId() != null) {
            wardLocationIds.add(parent.getId());
        }
    }

    /** 校对人 + 就诊复合查找键。 */
    private String buildLookupKey(Long performerCheckId, Long encounterId) {
        return performerCheckId + String.valueOf(LOOKUP_KEY_SEPARATOR) + encounterId;
    }

    /** 校对人 + 就诊锚点（明细扫描产出）。 */
    private static final class PerformerEncounterAnchor {

        /** 校对人主键。 */
        private Long performerCheckId;

        /** 就诊主键。 */
        private Long encounterId;
    }

    /** 校对人岗位范围：绑定 location、解析出的病区与病区→科室映射。 */
    private static final class PractitionerWardScope {

        /** 岗位直接绑定的 location（含病房等，解析前）。 */
        private final Set<Long> boundLocationIds = new LinkedHashSet<>();

        /** 岗位直属科室 org（护士少见，作回落）。 */
        private final Set<Long> directOrgIds = new LinkedHashSet<>();

        /** 解析后的病区 location 集合（保序）。 */
        private Set<Long> wardLocationIds = new LinkedHashSet<>();

        /** 病区 location → 上级科室 org。 */
        private final Map<Long, Long> orgIdByWardLocationId = new LinkedHashMap<>();
    }
}
