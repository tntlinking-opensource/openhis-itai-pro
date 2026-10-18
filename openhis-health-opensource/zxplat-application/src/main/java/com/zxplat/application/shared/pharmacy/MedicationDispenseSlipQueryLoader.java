/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pharmacy;

import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery;
import com.zxplat.domain.medication.medicationdispense.query.OutpatientPharmacyPendingEncounterPageQuery;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 发药单主表分页：关键字与医嘱状态跨聚合预解析（B.6.1）；OP/IP 共用。
 */
@Component
@RequiredArgsConstructor
public class MedicationDispenseSlipQueryLoader {

    /** 患者读侧：关键字命中患者姓名/拼音/五笔。 */
    private final PatientQueryService patientQueryService;
    /** 就诊读侧：关键字命中就诊 busNo。 */
    private final EncounterQueryService encounterQueryService;
    /** 药品申请读侧：按状态/科室预解析 medReqId。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 药品发放读侧：退药收口正向 medReqId 查询。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 写入 {@link MedicationDispenseSlipPageQuery} 的预解析就诊/药品申请主键，供发放域 Specification 使用。
     *
     * @param query 发药单分页入参；{@code null} 时无操作
     */
    public void enrichResolvedFilters(MedicationDispenseSlipPageQuery query) {
        if (query == null) {
            return;
        }
        this.enrichEncounterIdsForKeyword(query);
        this.enrichMedReqIdsForRequestStatus(query);
        this.enrichMedReqIdsForOrderingDepartment(query);
        this.enrichSupersededForwardMedReqIds(query);
    }

    /**
     * 门诊发药队列：预解析退药收口正向 medReqId 集合。
     *
     * @param query 门诊发药队列入参
     */
    public void enrichSupersededForwardMedReqIds(OutpatientPharmacyPendingEncounterPageQuery query) {
        if (query == null || !this.includesCompletedStatus(query.getDispenseStatusEnumIn())) {
            return;
        }
        query.setResolvedSupersededForwardMedReqIds(this.listSupersededForwardMedReqIds());
    }

    /**
     * 发药单分页查询含 COMPLETED 态时预填被退药 supersede 的正向 medReqId 集合。
     *
     * @param query 发药 slip 分页入参
     */
    private void enrichSupersededForwardMedReqIds(MedicationDispenseSlipPageQuery query) {
        if (query == null || !this.slipQueryIncludesCompletedStatus(query)) {
            return;
        }
        query.setResolvedSupersededForwardMedReqIds(this.listSupersededForwardMedReqIds());
    }

    /**
     * 列出已被确认退药收口的正向药品医嘱主键集合。
     *
     * @return 正向 {@code medReqId} 集合；无命中时返回空列表
     */
    private List<Long> listSupersededForwardMedReqIds() {
        List<Long> refundDispenseMedReqIds = medicationDispenseQueryService.listRefundedRefundDispenseMedReqIds();
        if (refundDispenseMedReqIds.isEmpty()) {
            return List.of();
        }
        List<MedicationRequest> refundRequests =
            medicationRequestQueryService.listByIds(refundDispenseMedReqIds);
        Set<Long> superseded = new LinkedHashSet<>();
        for (MedicationRequest refundReq : refundRequests) {
            if (refundReq == null || Boolean.TRUE.equals(refundReq.getIsDeleted())) {
                continue;
            }
            if (refundReq.getRefundMedicineId() != null) {
                superseded.add(refundReq.getRefundMedicineId());
            }
        }
        return new ArrayList<>(superseded);
    }

    /**
     * slip 分页是否筛选 COMPLETED（单值 status 或多选 dispenseStatus 任一击中）。
     *
     * @param query slip 分页入参
     * @return 含 COMPLETED 为 true
     */
    private boolean slipQueryIncludesCompletedStatus(MedicationDispenseSlipPageQuery query) {
        if (StringUtils.isNotEmpty(query.getStatusEnum())
            && DispenseStatus.COMPLETED.getCode().equals(query.getStatusEnum().trim())) {
            return true;
        }
        return this.includesCompletedStatus(query.getDispenseStatusEnumIn());
    }

    /**
     * 多选 dispenseStatus 是否包含 COMPLETED code。
     *
     * @param dispenseStatusEnumIn 状态 code 列表
     * @return 含 COMPLETED 为 true
     */
    private boolean includesCompletedStatus(List<String> dispenseStatusEnumIn) {
        if (dispenseStatusEnumIn == null || dispenseStatusEnumIn.isEmpty()) {
            return false;
        }
        for (String code : dispenseStatusEnumIn) {
            if (StringUtils.isEmpty(code)) {
                continue;
            }
            if (DispenseStatus.COMPLETED.getCode().equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关键字命中就诊 busNo 或患者姓名/拼音/五笔时，预解析 encounterId 集合写入 query。
     *
     * @param query 发药单分页入参
     */
    private void enrichEncounterIdsForKeyword(MedicationDispenseSlipPageQuery query) {
        if (StringUtils.isEmpty(query.getKeyword()) || query.getEncounterId() != null) {
            return;
        }
        String trimmed = query.getKeyword().trim();
        Set<Long> encounterIds = new LinkedHashSet<>();
        encounterIds.addAll(this.encounterQueryService.listEncounterIdsByBusNoKeyword(trimmed));
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(trimmed);
        if (!patientIds.isEmpty()) {
            encounterIds.addAll(this.encounterQueryService.listEncounterIdsByPatientIds(patientIds));
        }
        query.setResolvedEncounterIdsForKeyword(new ArrayList<>(encounterIds));
    }

    /**
     * 按医嘱状态预解析药品申请主键，供发放域 IN 过滤；无就诊范围时写空列表避免误查全表。
     *
     * @param query 发药单分页入参
     */
    private void enrichMedReqIdsForRequestStatus(MedicationDispenseSlipPageQuery query) {
        if (query.getRequestStatusEnumIn() == null || query.getRequestStatusEnumIn().isEmpty()) {
            return;
        }
        if (query.getEncounterId() == null && query.getResolvedEncounterIdsForKeyword() == null) {
            if (query.getPatientId() != null) {
                query.setResolvedMedReqIdsForRequestStatus(
                    this.medicationRequestQueryService.listMedicationRequestIdsByPatientScopeAndStatusIn(
                        query.getPatientId(),
                        query.getExcludeEncounterId(),
                        query.getRequestStatusEnumIn()));
                return;
            }
            query.setResolvedMedReqIdsForRequestStatus(Collections.emptyList());
            return;
        }
        List<Long> encounterScope = null;
        if (query.getEncounterId() == null && query.getResolvedEncounterIdsForKeyword() != null) {
            encounterScope = query.getResolvedEncounterIdsForKeyword();
        }
        List<Long> medReqIds = this.medicationRequestQueryService.listMedicationRequestIdsByEncounterScopeAndStatusIn(
            query.getEncounterId(),
            encounterScope,
            query.getRequestStatusEnumIn());
        query.setResolvedMedReqIdsForRequestStatus(medReqIds);
    }

    /**
     * 按开单科室（药嘱 {@code org_id}）预解析药品申请主键；仅当请求显式传入 {@code orderingDepartmentId} 时收窄。
     *
     * @param query 发药单分页入参
     */
    private void enrichMedReqIdsForOrderingDepartment(MedicationDispenseSlipPageQuery query) {
        if (query.getOrderingDepartmentId() == null) {
            return;
        }
        List<Long> medReqIds;
        if (query.getEncounterId() != null) {
            medReqIds = this.medicationRequestQueryService.listMedicationRequestIdsByEncounterAndOrgId(
                query.getEncounterId(), query.getOrderingDepartmentId());
        } else if (query.getPatientId() != null) {
            medReqIds = this.medicationRequestQueryService.listMedicationRequestIdsByPatientScopeAndOrgId(
                query.getPatientId(), query.getExcludeEncounterId(), query.getOrderingDepartmentId());
        } else if (query.getResolvedEncounterIdsForKeyword() != null) {
            medReqIds = this.medicationRequestQueryService.listMedicationRequestIdsByEncounterIdsAndOrgId(
                query.getResolvedEncounterIdsForKeyword(), query.getOrderingDepartmentId());
        } else {
            medReqIds = Collections.emptyList();
        }
        query.setResolvedMedReqIdsForOrderingDepartment(medReqIds);
    }
}
