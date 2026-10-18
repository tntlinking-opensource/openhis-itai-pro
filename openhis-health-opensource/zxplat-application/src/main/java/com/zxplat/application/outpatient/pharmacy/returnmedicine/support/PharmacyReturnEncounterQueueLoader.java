/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.returnmedicine.support;

import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 门诊药房退药患者队列：跨聚合读编排（B.6.1.3），预解析就诊收窄后委托 {@link MedicationDispenseQueryService}。
 */
@Component("outpatientPharmacyReturnEncounterQueueLoader")
@RequiredArgsConstructor
public class PharmacyReturnEncounterQueueLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;

    /**
     * 门诊药房退药患者队列：分页返回存在待退药或已退药药品发放事实的就诊主键。
     *
     * @param query 库房、时段、科室、关键字等
     * @return 就诊主键页（按退药状态变更时间倒序；单页上限 100）
     */
    public Page<Long> pageEncounterIdsForOutpatientPharmacyReturn(PageReturnPatientQuery query) {
        if (query == null) {
            query = new PageReturnPatientQuery();
        }
        if (query.getDepartmentId() != null || StringUtils.isNotEmpty(query.getKeyword())) {
            query.setResolvedEncounterScope(
                this.resolveOutpatientReturnEncounterScope(query.getDepartmentId(), query.getKeyword()));
        }
        return this.medicationDispenseQueryService.pageEncounterIdsForOutpatientPharmacyReturn(query);
    }

    /**
     * 门诊退药队列就诊收窄：科室与关键字取交集或并集。
     *
     * @param departmentId 开单科室；null 时不按科室过滤
     * @param keyword      就诊号/患者关键字；空时不按关键字过滤
     * @return 预解析就诊主键列表
     */
    private List<Long> resolveOutpatientReturnEncounterScope(Long departmentId, String keyword) {
        LinkedHashSet<Long> scope = new LinkedHashSet<>();
        if (departmentId != null) {
            scope.addAll(this.encounterQueryService.listOutpatientAmbEncounterIdsByOrganization(departmentId));
        }
        if (StringUtils.isNotEmpty(keyword)) {
            List<Long> keywordHits = this.resolveOutpatientAmbEncounterIdsByKeyword(keyword);
            if (departmentId != null) {
                return this.intersectEncounterIds(new ArrayList<>(scope), keywordHits);
            }
            return keywordHits;
        }
        return new ArrayList<>(scope);
    }

    /**
     * 门诊关键字解析就诊：就诊号与患者姓名/证件并集后过滤为门诊 amb 类。
     *
     * @param keyword 用户键入关键字
     * @return 匹配的门诊就诊主键
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
     * 两路就诊主键列表取交集（保持 left 顺序）；任一侧为空则返回空列表。
     *
     * @param left  基准就诊列表
     * @param right 收窄列表
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
