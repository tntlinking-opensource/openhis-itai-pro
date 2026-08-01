/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.support;

import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.openhis.health.domain.share.constants.NurseWorkloadConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.workflow.devicedispense.entity.DeviceDispense;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.shared.nursestation.DeviceAutoInventoryDeductSettingResolver;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

/**
 * 护士工作量读路径 scope 预解析（application/report/nurseworkload/support；AS-RPT-002 IF-RPT-NWL-02～05）。
 *
 * <p>编排 Encounter / ServiceRequest / DeviceDefinition / DeviceDispense 读侧；不写库。
 * 须在调用 {@code ProcedureQueryService} / {@code DeviceDispenseQueryService} 护士工作量方法前执行。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseWorkloadLoader {

    private final EncounterQueryService encounterQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final DeviceAutoInventoryDeductSettingResolver deviceAutoInventoryDeductSettingResolver;

    /**
     * 预解析就诊 scope、耗材开关及 SR/耗材定义 enrich map。
     *
     * @param query 须含 {@code encounterScope}、{@code dateFrom}/{@code dateTo}
     * @throws IllegalArgumentException 参数非法（与 AS EX-RPT-NWL-102 语义对齐，由应用层转换提示语）
     */
    public void prepareNurseWorkloadQueryScopes(NurseWorkloadPageQuery query) {
        // Gate：报表必填筛选项，非法直接拒绝（Support 层抛 IAE，应用层映射用户提示）
        if (query == null) {
            throw new IllegalArgumentException("nurse workload query is required");
        }
        if (StringUtils.isEmpty(query.getEncounterScope())) {
            throw new IllegalArgumentException("encounterScope is required");
        }
        if (query.getDateFrom() == null || query.getDateTo() == null) {
            throw new IllegalArgumentException("dateFrom and dateTo are required");
        }
        if (query.getDateFrom().isAfter(query.getDateTo())) {
            throw new IllegalArgumentException("dateFrom must not be after dateTo");
        }
        // Pre：门诊/住院 → Encounter.class_enum，并按统计日窗收窄就诊（禁止无日期全历史）
        String encounterClassCode = this.resolveEncounterClassCode(query.getEncounterScope());
        query.setResolvedEncounterClassCode(encounterClassCode);
        LocalDateTime windowFrom = query.getDateFrom().atStartOfDay();
        LocalDateTime windowTo = query.getDateTo().atTime(23, 59, 59);
        // Pre：经 Encounter QS 解析护士工作量就诊 scope（B.6.4；日窗相交）
        List<Long> encounterScope = this.encounterQueryService.listEncounterIdsForNurseWorkloadScope(
            encounterClassCode, windowFrom, windowTo);
        query.setResolvedEncounterScope(encounterScope);
        query.setDeviceAutoInventoryDeductEnabled(this.deviceAutoInventoryDeductSettingResolver.isEnabled());
        if (encounterScope.isEmpty()) {
            query.setCategoryEnumByServiceRequestId(Map.of());
            query.setHvcmFlagByDeviceDefId(Map.of());
            return;
        }
        // Pre：诊疗汇总/明细所需的 SR.category_enum（跨聚合读，经 ServiceRequest QS）
        query.setCategoryEnumByServiceRequestId(
            this.serviceRequestQueryService.mapCategoryEnumByEncounterIds(encounterScope));
        // Pre：耗材汇总/明细所需的 hvcm_flag（先取候选发放行再批量查 DeviceDefinition）
        query.setHvcmFlagByDeviceDefId(this.buildHvcmFlagMapForConsumableCandidates(query));
    }

    /**
     * 按候选耗材发放行收集 deviceDefId 并批量解析 hvcm_flag。
     *
     * @param query 已写入 encounterScope 与耗材开关的预解析查询
     * @return deviceDefId → hvcmFlag
     */
    private Map<Long, Boolean> buildHvcmFlagMapForConsumableCandidates(NurseWorkloadPageQuery query) {
        List<DeviceDispense> candidates =
            this.deviceDispenseQueryService.listNurseConsumableWorkloadCandidateDispenses(query);
        Set<Long> deviceDefIds = new LinkedHashSet<>();
        for (DeviceDispense row : candidates) {
            if (row != null && row.getDeviceDefId() != null) {
                deviceDefIds.add(row.getDeviceDefId());
            }
        }
        if (deviceDefIds.isEmpty()) {
            return Map.of();
        }
        List<DeviceDefinition> definitions = this.deviceDefinitionQueryService.getAllDeviceDefinitions(deviceDefIds);
        Map<Long, Boolean> hvcmByDeviceDefId = new HashMap<>();
        for (DeviceDefinition definition : definitions) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            hvcmByDeviceDefId.put(definition.getId(), definition.getHvcmFlag());
        }
        return hvcmByDeviceDefId;
    }

    /**
     * 将页面 encounterScope 映射为 Encounter 存储码。
     *
     * @param encounterScope OUTPATIENT / INPATIENT
     * @return AMB / IMP
     */
    private String resolveEncounterClassCode(String encounterScope) {
        if (NurseWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT.equals(encounterScope)) {
            return EncounterClass.IMP.getCode();
        }
        if (NurseWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(encounterScope)) {
            return EncounterClass.AMB.getCode();
        }
        throw new IllegalArgumentException("unsupported encounterScope: " + encounterScope);
    }
}
