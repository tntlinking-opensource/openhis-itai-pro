/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.support;

import com.openhis.health.application.shared.pharmacy.MedicationDispenseSlipQueryLoader;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.medication.medicationdispense.query.OutpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 门诊药房发药患者队列：跨聚合读编排（B.6.1.3），预解析就诊收窄后委托 {@link MedicationDispenseQueryService}。
 */
@Component("outpatientPharmacyEncounterQueueLoader")
@RequiredArgsConstructor
public class PharmacyEncounterQueueLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final MedicationDispenseSlipQueryLoader medicationDispenseSlipQueryLoader;

    /**
     * 门诊发药队列：分页返回存在门诊药品发放事实的就诊主键。
     *
     * @param query 库房、发放状态、可选日期窗/关键字等；{@code null} 时按空条件
     * @return 就诊主键页（按发放创建时间倒序）
     */
    public Page<Long> pageEncounterIdsForOutpatientPharmacyPending(OutpatientPharmacyPendingEncounterPageQuery query) {
        if (query == null) {
            query = new OutpatientPharmacyPendingEncounterPageQuery();
        }
        // Pre：日期窗（就诊 startTime）∩ 关键字，与门诊收费队列同口径。
        query.setResolvedEncounterScope(this.resolveOutpatientPharmacyEncounterScope(query));
        this.medicationDispenseSlipQueryLoader.enrichSupersededForwardMedReqIds(query);
        return this.medicationDispenseQueryService.pageEncounterIdsForOutpatientPharmacyPending(query);
    }

    /**
     * 门诊发药队列就诊收窄：可选日期窗 AMB 基线与关键字并集求交（对齐收费 {@code OutpatientChargeEncounterQueueLoader}）。
     *
     * @param query 含 {@code startDate}/{@code endDate}/{@code keyword}
     * @return 去重就诊主键；无日期且无关键字时返回 {@code null}（不额外收窄）
     */
    private List<Long> resolveOutpatientPharmacyEncounterScope(OutpatientPharmacyPendingEncounterPageQuery query) {
        boolean hasDateWindow = query.getStartDate() != null || query.getEndDate() != null;
        boolean hasKeyword = StringUtils.isNotEmpty(query.getKeyword());
        if (!hasDateWindow && !hasKeyword) {
            return null;
        }
        List<Long> dateScope = null;
        if (hasDateWindow) {
            // Pre：按就诊 startTime 收敛 AMB（与卡片「就诊时间」同轴）
            dateScope = this.encounterQueryService.listOutpatientAmbEncounterIdsForCashierScope(
                null, query.getStartDate(), query.getEndDate());
        }
        if (!hasKeyword) {
            return dateScope;
        }
        List<Long> keywordScope = this.resolveOutpatientAmbEncounterIdsByKeyword(query.getKeyword());
        if (dateScope == null) {
            return keywordScope;
        }
        // 日期 ∩ 关键字
        LinkedHashSet<Long> dateSet = new LinkedHashSet<>(dateScope);
        List<Long> intersection = new ArrayList<>();
        for (Long encounterId : keywordScope) {
            if (encounterId != null && dateSet.contains(encounterId)) {
                intersection.add(encounterId);
            }
        }
        return intersection;
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
}
