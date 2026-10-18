/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.support;

import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.zxplat.domain.share.constants.NurseWorkloadConstants;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.workflow.devicedispense.entity.DeviceDispense;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.application.shared.nursestation.DeviceAutoInventoryDeductSettingResolver;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 须在调用 {@code ProcedureQueryService} / {@code DeviceDispenseQueryService} 护士工作量方法前执行。
 * 诊疗与耗材 IF 可按需只 enrich 本侧 map，避免互扫无关聚合。</p>
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
     * 预解析就诊 scope，并 enrich 诊疗类目与耗材 hvcm（兼容旧调用，两侧均加载）。
     *
     * @param query 须含 {@code encounterScope}、{@code dateFrom}/{@code dateTo}
     * @throws IllegalArgumentException 参数非法（与 AS EX-RPT-NWL-102 语义对齐，由应用层转换提示语）
     */
    public void prepareNurseWorkloadQueryScopes(NurseWorkloadPageQuery query) {
        this.prepareNurseWorkloadQueryScopes(query, true, true);
    }

    /**
     * 按需预解析 scope：仅加载当前 IF 需要的跨聚合 enrich map。
     *
     * @param query                 须含 {@code encounterScope}、{@code dateFrom}/{@code dateTo}
     * @param needExecutionCategory {@code true} 时批量 enrich SR.category_enum（诊疗 IF）
     * @param needConsumableHvcm    {@code true} 时批量 enrich 耗材 hvcm_flag（耗材 IF）
     * @throws IllegalArgumentException 参数非法
     */
    public void prepareNurseWorkloadQueryScopes(
        NurseWorkloadPageQuery query,
        boolean needExecutionCategory,
        boolean needConsumableHvcm) {
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
        // Pre：诊疗 IF 才拉 SR.category_enum；耗材 IF 跳过，避免无谓词扫全就诊 SR
        if (needExecutionCategory) {
            query.setCategoryEnumByServiceRequestId(
                this.serviceRequestQueryService.mapCategoryEnumByEncounterIds(encounterScope));
        } else {
            query.setCategoryEnumByServiceRequestId(Map.of());
        }
        // Pre：耗材 IF 才拉候选发放 + hvcm；诊疗 IF 跳过
        if (needConsumableHvcm) {
            query.setHvcmFlagByDeviceDefId(this.buildHvcmFlagMapForConsumableCandidates(query));
        } else {
            query.setHvcmFlagByDeviceDefId(Map.of());
        }
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
