/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.workflow.devicedispense.query.ConsumableUsageReportPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.query.DeptIssueReportPageQuery;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 耗材使用报表跨聚合筛选预解析（品规/患者关键字；B.6.4）。
 *
 * <p>写入 {@link ConsumableUsageReportPageQuery} / {@link DeptIssueReportPageQuery} 的预解析字段；
 * 不写库。科室/库位/就诊 scope 仍由 {@link ConsumableUsageReportScopeLoader} 负责。</p>
 */
@Component
@RequiredArgsConstructor
public class ConsumableUsageReportFilterLoader {

    /** 耗材定义读侧：类型与关键字 → device_def_id。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /** 就诊读侧：患者关键字 → encounterId。 */
    private final EncounterQueryService encounterQueryService;

    /** 患者读侧：列表关键字 → patientId。 */
    private final PatientQueryService patientQueryService;

    /**
     * 患者侧：叠加记录类型、执行人、品规预解析、患者关键字收窄 encounter scope。
     *
     * @param query              已完成时间窗/库位/就诊 scope 的患者侧 Query
     * @param filter             报表筛选；不可为 null
     * @param encounterClassCode {@link EncounterClass#AMB} 或 {@link EncounterClass#IMP}
     */
    public void applyPatientExtraFilters(
        ConsumableUsageReportPageQuery query,
        ConsumableUsageReportQueryFilter filter,
        String encounterClassCode) {
        if (query == null || filter == null) {
            return;
        }
        // Pre：记录类型（空回落 ALL）
        String recordType = StringUtils.trimToNull(filter.getRecordTypeCode());
        if (recordType == null) {
            recordType = ConsumableUsageReportConstants.RECORD_TYPE_ALL;
        }
        query.setRecordTypeCode(recordType);
        // Pre：执行人精确筛
        query.setPerformerId(filter.getPerformerPractitionerId());
        // Pre：类型∩关键字 → device_def_id IN；无筛不写
        List<Long> deviceDefIds =
            this.resolveDeviceDefIds(filter.getDeviceCategoryCodes(), filter.getDeviceKeyword());
        if (deviceDefIds != null) {
            query.setResolvedDeviceDefIds(deviceDefIds);
        }
        // Pre：患者关键字与既有 AMB/IMP scope 取交
        this.applyPatientKeywordToEncounterScope(query, filter.getPatientKeyword(), encounterClassCode);
    }

    /**
     * 科室领用侧：按类型/关键字写 {@code resolvedItemIds}。
     *
     * @param query  已完成时间窗/库位的领用 Query
     * @param filter 报表筛选；不可为 null
     */
    public void applyDeptExtraFilters(DeptIssueReportPageQuery query, ConsumableUsageReportQueryFilter filter) {
        if (query == null || filter == null) {
            return;
        }
        // Pre：品规预解析；null=不筛；空列表=拒查
        List<Long> itemIds =
            this.resolveDeviceDefIds(filter.getDeviceCategoryCodes(), filter.getDeviceKeyword());
        if (itemIds != null) {
            query.setResolvedItemIds(itemIds);
        }
    }

    /**
     * 按耗材类型与/或关键字解析 device_def_id；两者同时存在时取交集。
     *
     * @param deviceCategoryCodes 类型多选
     * @param deviceKeyword       名称/拼音/编码
     * @return 预解析 id；无筛选 null；有筛选无命中空列表
     */
    public List<Long> resolveDeviceDefIds(List<String> deviceCategoryCodes, String deviceKeyword) {
        boolean hasCategory = deviceCategoryCodes != null && !deviceCategoryCodes.isEmpty();
        boolean hasKeyword = StringUtils.isNotEmpty(deviceKeyword);
        // 无类型且无关键字：不写 resolved，领域不过滤品规
        if (!hasCategory && !hasKeyword) {
            return null;
        }
        LinkedHashSet<Long> union = new LinkedHashSet<>();
        boolean initialized = false;
        // 类型多选 → device_definition.id IN
        if (hasCategory) {
            List<Long> byCategory = this.deviceDefinitionQueryService.listIdsByCategoryCodesIn(deviceCategoryCodes);
            union.addAll(byCategory);
            initialized = true;
        }
        // 关键字 → 目录模糊；与类型同时存在时取交集
        if (hasKeyword) {
            List<Long> byKeyword =
                this.deviceDefinitionQueryService.listIdsByInventoryDetailKeyword(deviceKeyword.trim());
            if (!initialized) {
                if (byKeyword != null) {
                    union.addAll(byKeyword);
                }
            } else {
                LinkedHashSet<Long> keywordSet = new LinkedHashSet<>();
                if (byKeyword != null) {
                    keywordSet.addAll(byKeyword);
                }
                union.retainAll(keywordSet);
            }
        }
        return new ArrayList<>(union);
    }

    /**
     * 患者关键字与既有就诊 scope 取交；无关键字跳过。
     *
     * @param query              患者侧 Query
     * @param patientKeyword     姓名/就诊号等
     * @param encounterClassCode AMB / IMP
     */
    private void applyPatientKeywordToEncounterScope(
        ConsumableUsageReportPageQuery query,
        String patientKeyword,
        String encounterClassCode) {
        if (StringUtils.isEmpty(patientKeyword)) {
            return;
        }
        String keyword = patientKeyword.trim();
        List<Long> keywordEncounters;
        // 门诊 AMB 与住院 IMP 关键字解析口径不同，禁止串域
        if (EncounterClass.IMP.getCode().equals(encounterClassCode)) {
            keywordEncounters = this.resolveInpatientImpEncounterIdsByKeyword(keyword);
        } else {
            keywordEncounters = this.resolveOutpatientAmbEncounterIdsByKeyword(keyword);
        }
        List<Long> currentScope = query.getResolvedEncounterScope();
        // 尚无 scope：直接写入关键字命中（空列表拒查）
        if (currentScope == null) {
            query.setResolvedEncounterScope(keywordEncounters);
            return;
        }
        // 与已有 AMB/IMP/费用性质 scope 取交
        LinkedHashSet<Long> keywordSet = new LinkedHashSet<>(keywordEncounters);
        List<Long> intersection = new ArrayList<>();
        for (Long encounterId : currentScope) {
            if (encounterId != null && keywordSet.contains(encounterId)) {
                intersection.add(encounterId);
            }
        }
        query.setResolvedEncounterScope(intersection);
    }

    /**
     * 门诊患者关键字 → amb 就诊 id。
     *
     * @param keyword 已 trim
     * @return encounterId 列表
     */
    private List<Long> resolveOutpatientAmbEncounterIdsByKeyword(String keyword) {
        LinkedHashSet<Long> union = new LinkedHashSet<>();
        union.addAll(this.encounterQueryService.filterOutpatientAmbEncounterIds(
            this.encounterQueryService.listEncounterIdsByBusNoKeyword(keyword)));
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
        union.addAll(this.encounterQueryService.filterOutpatientAmbEncounterIds(
            this.encounterQueryService.listEncounterIdsByPatientIds(patientIds)));
        return new ArrayList<>(union);
    }

    /**
     * 住院患者关键字 → imp 就诊 id。
     *
     * @param keyword 已 trim
     * @return encounterId 列表
     */
    private List<Long> resolveInpatientImpEncounterIdsByKeyword(String keyword) {
        LinkedHashSet<Long> union = new LinkedHashSet<>();
        union.addAll(this.collectEncounterIds(
            this.encounterQueryService.listInpatientEncounterEntitiesByIds(
                this.encounterQueryService.listEncounterIdsByBusNoKeyword(keyword))));
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
        union.addAll(this.collectEncounterIds(
            this.encounterQueryService.listInpatientEncounterEntitiesByIds(
                this.encounterQueryService.listEncounterIdsByPatientIds(patientIds))));
        return new ArrayList<>(union);
    }

    /**
     * 从就诊实体提取非空 id。
     *
     * @param encounters 就诊列表
     * @return id 列表
     */
    private List<Long> collectEncounterIds(List<Encounter> encounters) {
        List<Long> ids = new ArrayList<>();
        if (encounters == null) {
            return ids;
        }
        for (Encounter encounter : encounters) {
            if (encounter != null && encounter.getId() != null) {
                ids.add(encounter.getId());
            }
        }
        return ids;
    }
}
