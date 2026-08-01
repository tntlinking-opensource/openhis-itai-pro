/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial.cashiersettlement;

import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 交账行展示名 enrich（提交人、审批人、药房、科室）。
 *
 * <p>批量读 Practitioner / Location / Organization，避免列表 N+1。
 * 展示名失败只打日志，不影响列表主数据返回。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CashierSettlementPartyNamesEnricher {

    /** 医务人员读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 位置（药房）读侧。 */
    private final LocationQueryService locationQueryService;

    /** 机构（科室）读侧。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 批量回填列表行展示名。
     *
     * @param rows 列表行；可空
     */
    public void enrich(List<? extends CashierSettlementPartyNamedRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        try {
            Set<Long> practitionerIds = new LinkedHashSet<>();
            Set<Long> locationIds = new LinkedHashSet<>();
            Set<Long> orgIds = new LinkedHashSet<>();
            for (CashierSettlementPartyNamedRow row : rows) {
                if (row == null) {
                    continue;
                }
                this.collectId(practitionerIds, row.getSubmitterId());
                this.collectId(practitionerIds, row.getApproverId());
                this.collectId(locationIds, row.getLocationId());
                this.collectId(orgIds, row.getOrgId());
            }
            Map<Long, String> practitionerNames = this.loadPractitionerNames(practitionerIds);
            Map<Long, String> locationNames = this.loadPharmacyLocationNames(locationIds);
            Map<Long, String> orgNames = this.loadOrgNames(orgIds);
            for (CashierSettlementPartyNamedRow row : rows) {
                if (row == null) {
                    continue;
                }
                row.setSubmitterName(practitionerNames.get(row.getSubmitterId()));
                row.setApproverName(practitionerNames.get(row.getApproverId()));
                row.setLocationName(locationNames.get(row.getLocationId()));
                row.setOrgName(orgNames.get(row.getOrgId()));
            }
        } catch (RuntimeException runtimeException) {
            // 展示名是附属字段：查不到或 enrich 异常时留空，列表主数据仍返回
            log.warn("交账展示名 enrich 失败，列表仍返回主键与业务字段", runtimeException);
        }
    }

    /**
     * 收集非空主键。
     *
     * @param target 目标集合
     * @param id     主键
     */
    private void collectId(Set<Long> target, Long id) {
        if (id != null) {
            target.add(id);
        }
    }

    /**
     * 批量加载医务人员姓名。
     *
     * @param practitionerIds 医务人员主键
     * @return id → 姓名
     */
    private Map<Long, String> loadPractitionerNames(Collection<Long> practitionerIds) {
        Map<Long, String> names = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return names;
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        if (practitioners == null) {
            return names;
        }
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            names.put(practitioner.getId(), practitioner.getName());
        }
        return names;
    }

    /**
     * 批量加载药房位置名称（含科室药房形态）。
     *
     * @param locationIds 位置主键
     * @return id → 名称
     */
    private Map<Long, String> loadPharmacyLocationNames(Collection<Long> locationIds) {
        Map<Long, String> names = new HashMap<>();
        if (locationIds == null || locationIds.isEmpty()) {
            return names;
        }
        List<Location> pharmacies = this.locationQueryService.findActivePharmaciesByIds(locationIds);
        if (pharmacies == null) {
            return names;
        }
        for (Location location : pharmacies) {
            if (location == null || location.getId() == null) {
                continue;
            }
            names.put(location.getId(), location.getName());
        }
        return names;
    }

    /**
     * 批量加载科室名称。
     *
     * @param orgIds 科室主键
     * @return id → 名称
     */
    private Map<Long, String> loadOrgNames(Collection<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> orgNames = this.organizationQueryService.findOrganizationNamesByIds(orgIds);
        if (orgNames == null) {
            return Collections.emptyMap();
        }
        return orgNames;
    }
}
