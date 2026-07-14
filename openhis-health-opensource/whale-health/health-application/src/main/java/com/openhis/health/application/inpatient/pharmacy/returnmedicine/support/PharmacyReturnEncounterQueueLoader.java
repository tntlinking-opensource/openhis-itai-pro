/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.returnmedicine.support;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyReturnEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 住院药房退药患者队列：跨聚合读编排（B.6.1.3），预解析在院就诊收窄后委托 {@link MedicationDispenseQueryService}。
 */
@Component("inpatientPharmacyReturnEncounterQueueLoader")
@RequiredArgsConstructor
public class PharmacyReturnEncounterQueueLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final LocationQueryService locationQueryService;

    /**
     * 住院药房退药患者队列：分页返回存在待退/已退药品发放事实且在院就诊的主键。
     *
     * @param query 库房、病区、关键字等
     * @return 就诊主键页（按退药状态变更时间倒序；单页上限 100）
     */
    public Page<Long> pageEncounterIdsForInpatientPharmacyReturn(InpatientPharmacyReturnEncounterPageQuery query) {
        if (query == null) {
            query = new InpatientPharmacyReturnEncounterPageQuery();
        }
        List<Long> bedIdsUnderWard = null;
        if (query.getWardId() != null) {
            bedIdsUnderWard = this.locationQueryService.listActiveBedIdsUnderWard(query.getWardId());
        }
        List<Long> scope = this.encounterQueryService.listInpatientInHospitalEncounterIds(
            query.getWardId(), bedIdsUnderWard);
        if (StringUtils.isNotEmpty(query.getKeyword())) {
            scope = this.intersectEncounterIds(scope, this.resolveInpatientEncounterIdsByKeyword(query.getKeyword()));
        }
        query.setResolvedEncounterScope(scope);
        return this.medicationDispenseQueryService.pageEncounterIdsForInpatientPharmacyReturn(query);
    }

    /**
     * 住院在院关键字解析就诊：就诊号、患者与床位名称并集后过滤为在院住院。
     *
     * @param keyword 用户键入关键字
     * @return 匹配的在院住院就诊主键
     */
    private List<Long> resolveInpatientEncounterIdsByKeyword(String keyword) {
        LinkedHashSet<Long> union = new LinkedHashSet<>();
        union.addAll(this.encounterQueryService.filterInpatientInHospitalEncounterIds(
            this.encounterQueryService.listEncounterIdsByBusNoKeyword(keyword)));
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
        union.addAll(this.encounterQueryService.filterInpatientInHospitalEncounterIds(
            this.encounterQueryService.listEncounterIdsByPatientIds(patientIds)));
        List<Long> bedIds = this.locationQueryService.listActiveBedIdsByNameKeyword(keyword);
        union.addAll(this.encounterQueryService.listInpatientInHospitalEncounterIdsByActiveBedLocationIds(bedIds));
        return new ArrayList<>(union);
    }

    /**
     * 两路就诊主键列表取交集（保持 left 顺序）；任一侧为空则返回空列表。
     *
     * @param left  基准就诊列表（如病区在院集合）
     * @param right 收窄列表（如关键字命中）
     * @return 交集主键
     */
    private List<Long> intersectEncounterIds(List<Long> left, List<Long> right) {
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> rightSet = new LinkedHashSet<>(right);
        LinkedHashSet<Long> intersection = new LinkedHashSet<>();
        for (Long id : left) {
            if (id != null && rightSet.contains(id)) {
                intersection.add(id);
            }
        }
        return new ArrayList<>(intersection);
    }
}
