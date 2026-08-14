/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.constants.DeptIssueReportConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.workflow.devicedispense.query.ConsumableUsageReportPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.query.DeptIssueReportPageQuery;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 耗材使用报表跨聚合 scope 预解析（AS-RPT-005/006；门诊 AMB / 住院 IMP）。
 *
 * <p>写入 {@link ConsumableUsageReportPageQuery} 与 {@link DeptIssueReportPageQuery} 的预解析字段；
 * 不写库。科室领用库位按门诊挂号/住院入院组织树收窄科室药房。</p>
 */
@Component
@RequiredArgsConstructor
public class ConsumableUsageReportScopeLoader {

    /** 就诊读侧：AMB/IMP scope 与就诊→科室映射。 */
    private final EncounterQueryService encounterQueryService;

    /** 库位读侧：科室药房与组织归属。 */
    private final LocationQueryService locationQueryService;

    /** 组织读侧：门诊/住院相关科室树。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 预解析患者侧 Query（无科室筛兼容重载）。
     *
     * @param query              患者侧领域 Query
     * @param encounterClassCode AMB / IMP
     * @param dateFrom           业务日起
     * @param dateTo             业务日止
     * @param locationId         可选库位
     * @param summaryDimension   汇总维；可空
     */
    public void preparePatientDispenseQuery(
        ConsumableUsageReportPageQuery query,
        String encounterClassCode,
        String dateFrom,
        String dateTo,
        Long locationId,
        String summaryDimension) {
        this.preparePatientDispenseQuery(
            query, encounterClassCode, dateFrom, dateTo, locationId, summaryDimension, null);
    }

    /**
     * 预解析患者侧 Query：时间窗、就诊 scope、科室维组织映射。
     *
     * @param query              患者侧领域 Query；不可为 null
     * @param encounterClassCode {@link EncounterClass#AMB} 或 {@link EncounterClass#IMP}
     * @param dateFrom           业务日起 yyyy-MM-dd
     * @param dateTo             业务日止 yyyy-MM-dd
     * @param locationId         可选单库位筛选
     * @param summaryDimension   汇总维；可空（明细不强制）
     * @param requestingOrgId    开立/就诊科室；可空
     */
    public void preparePatientDispenseQuery(
        ConsumableUsageReportPageQuery query,
        String encounterClassCode,
        String dateFrom,
        String dateTo,
        Long locationId,
        String summaryDimension,
        Long requestingOrgId) {
        // Pre：闭区间日窗写入 dispense_time 上下界
        query.setBusinessTimeFrom(this.toDayStart(dateFrom));
        query.setBusinessTimeTo(this.toDayEnd(dateTo));
        query.setLocationId(locationId);
        String dimension = StringUtils.trimToNull(summaryDimension);
        if (dimension != null) {
            query.setSummaryDimension(dimension);
        }
        // Pre：门诊/住院就诊主键 scope（B.6.4；叠加业务日窗，禁止 class 全历史）
        LocalDateTime windowFrom = this.toDayStart(dateFrom);
        LocalDateTime windowTo = this.toDayEnd(dateTo);
        List<Long> encounterScope =
            this.encounterQueryService.listEncounterIdsForNurseWorkloadScope(
                encounterClassCode, windowFrom, windowTo);
        // Pre：开立/就诊科室筛 — 以 Encounter.organization_id 收窄（与科室维展示同锚）
        if (requestingOrgId != null && encounterScope != null && !encounterScope.isEmpty()) {
            Map<Long, Long> orgByEncounter = this.mapOrganizationIdByEncounterIds(encounterScope);
            List<Long> filtered = new ArrayList<>();
            for (Long encounterId : encounterScope) {
                if (requestingOrgId.equals(orgByEncounter.get(encounterId))) {
                    filtered.add(encounterId);
                }
            }
            encounterScope = filtered;
        }
        query.setResolvedEncounterScope(encounterScope);
        // Pre：科室维汇总须预写 encounter→organization
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)
            && encounterScope != null
            && !encounterScope.isEmpty()) {
            query.setOrganizationIdByEncounterId(this.mapOrganizationIdByEncounterIds(encounterScope));
        }
    }

    /**
     * 预解析科室领用侧 Query（无领用科室筛兼容重载）。
     *
     * @param query              科室侧领域 Query
     * @param encounterClassCode AMB / IMP
     * @param dateFrom           业务日起
     * @param dateTo             业务日止
     * @param purposeLocationId  可选库位
     * @param summaryDimension   汇总维；可空
     */
    public void prepareDeptIssueQuery(
        DeptIssueReportPageQuery query,
        String encounterClassCode,
        String dateFrom,
        String dateTo,
        Long purposeLocationId,
        String summaryDimension) {
        this.prepareDeptIssueQuery(
            query, encounterClassCode, dateFrom, dateTo, purposeLocationId, summaryDimension, null);
    }

    /**
     * 预解析科室领用侧 Query：时间窗、耗材 item_table、目的库位 scope、科室维映射。
     *
     * @param query                   科室侧领域 Query；不可为 null
     * @param encounterClassCode      AMB / IMP，用于组织树收窄科室药房
     * @param dateFrom                业务日起
     * @param dateTo                  业务日止
     * @param purposeLocationId       可选单库位
     * @param summaryDimension        汇总维；可空
     * @param consumingOrganizationId 领用科室；可空
     */
    public void prepareDeptIssueQuery(
        DeptIssueReportPageQuery query,
        String encounterClassCode,
        String dateFrom,
        String dateTo,
        Long purposeLocationId,
        String summaryDimension,
        Long consumingOrganizationId) {
        // Pre：耗材报表固定 item_table=adm_device_definition，避免与药品混页
        query.setItemTable(TableNameConstants.ADM_DEVICE_DEFINITION);
        query.setBusinessTimeFrom(this.toDayStart(dateFrom));
        query.setBusinessTimeTo(this.toDayEnd(dateTo));
        query.setPurposeLocationId(purposeLocationId);
        String dimension = StringUtils.trimToNull(summaryDimension);
        if (dimension != null) {
            // Pre：LOCATION 对外维 remap 为科室领域 PHARMACY，避免领域不识 LOCATION
            if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(dimension)) {
                query.setSummaryDimension(DeptIssueReportConstants.SUMMARY_DIMENSION_PHARMACY);
            } else {
                query.setSummaryDimension(dimension);
            }
        }
        // Pre：按门诊/住院组织树收窄科室药房；空列表表示本域无相关库位
        if (purposeLocationId == null) {
            List<Long> locationIds = this.resolveDeptPharmacyLocationIds(encounterClassCode);
            // 领用科室筛：仅保留归属该科室的药房（历史空 consuming_org 回落）
            if (consumingOrganizationId != null) {
                Map<Long, Long> orgByLocation = this.mapOrganizationIdByLocationIds(locationIds);
                List<Long> filtered = new ArrayList<>();
                for (Long locationId : locationIds) {
                    if (consumingOrganizationId.equals(orgByLocation.get(locationId))) {
                        filtered.add(locationId);
                    }
                }
                locationIds = filtered;
            }
            query.setResolvedPurposeLocationIds(locationIds);
        }
        if (consumingOrganizationId != null) {
            query.setConsumingOrganizationId(consumingOrganizationId);
        }
        // Pre：仅 DEPARTMENT 维预写 purposeLocation→organization；LOCATION 勿当科室维
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)
            || DeptIssueReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
            List<Long> purposeIds = new ArrayList<>();
            if (purposeLocationId != null) {
                purposeIds.add(purposeLocationId);
            } else if (query.getResolvedPurposeLocationIds() != null) {
                purposeIds.addAll(query.getResolvedPurposeLocationIds());
            }
            query.setOrganizationIdByPurposeLocationId(this.mapOrganizationIdByLocationIds(purposeIds));
        }
    }

    /**
     * 批量解析就诊 → 开单/就诊科室组织主键。
     *
     * @param encounterIds 就诊主键集合
     * @return encounterId → organizationId
     */
    public Map<Long, Long> mapOrganizationIdByEncounterIds(List<Long> encounterIds) {
        Map<Long, Long> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        List<Encounter> rows = this.encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        for (Encounter row : rows) {
            if (row == null || row.getId() == null || row.getOrganizationId() == null) {
                continue;
            }
            result.put(row.getId(), row.getOrganizationId());
        }
        return result;
    }

    /**
     * 按门诊挂号或住院入院组织树，收集启用中的科室药房主键。
     *
     * @param encounterClassCode AMB / IMP
     * @return 科室药房 locationId 列表；无命中时为空列表（领域侧拒查空结果）
     */
    public List<Long> resolveDeptPharmacyLocationIds(String encounterClassCode) {
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        List<Organization> organizations;
        // Pre：门诊/住院相关科室组织树不同，避免两报表科室领用串域
        if (EncounterClass.IMP.getCode().equals(encounterClassCode)) {
            organizations = this.organizationQueryService.listOrganizationsForInpatientAdmission();
        } else {
            organizations = this.organizationQueryService.listOrganizationsForOutpatientRegistration();
        }
        for (Organization organization : organizations) {
            if (organization != null && organization.getId() != null) {
                orgIds.add(organization.getId());
            }
        }
        List<Long> purposeLocationIds = new ArrayList<>();
        if (orgIds.isEmpty()) {
            return purposeLocationIds;
        }
        List<Location> departmentPharmacies = this.locationQueryService.listAllActiveDepartmentPharmacies();
        for (Location location : departmentPharmacies) {
            if (location == null || location.getId() == null || location.getOrganizationId() == null) {
                continue;
            }
            if (orgIds.contains(location.getOrganizationId())) {
                purposeLocationIds.add(location.getId());
            }
        }
        return purposeLocationIds;
    }

    /**
     * 批量解析库位 → 所属组织主键。
     *
     * @param locationIds 库位主键
     * @return locationId → organizationId
     */
    public Map<Long, Long> mapOrganizationIdByLocationIds(List<Long> locationIds) {
        Map<Long, Long> result = new HashMap<>();
        if (locationIds == null || locationIds.isEmpty()) {
            return result;
        }
        Map<Long, Location> locationById = this.locationQueryService.loadLocationEntityMapByIds(locationIds);
        for (Map.Entry<Long, Location> entry : locationById.entrySet()) {
            Location location = entry.getValue();
            if (location == null || location.getOrganizationId() == null) {
                continue;
            }
            result.put(entry.getKey(), location.getOrganizationId());
        }
        return result;
    }

    /**
     * 将 yyyy-MM-dd 转为当日 00:00:00。
     *
     * @param dateText 日期文本
     * @return 日初
     */
    public LocalDateTime toDayStart(String dateText) {
        LocalDate date = LocalDate.parse(dateText.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        return date.atStartOfDay();
    }

    /**
     * 将 yyyy-MM-dd 转为当日 23:59:59（闭区间上界）。
     *
     * @param dateText 日期文本
     * @return 日末
     */
    public LocalDateTime toDayEnd(String dateText) {
        LocalDate date = LocalDate.parse(dateText.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        return date.atTime(LocalTime.of(23, 59, 59));
    }
}
