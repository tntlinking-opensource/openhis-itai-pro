/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.querysupport.pharmacy;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.OutpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.enums.DispenseStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 发药单主表分页：关键字与医嘱状态跨聚合预解析（B.6.1）。
 */
@Component
@RequiredArgsConstructor
public class PharmacyDispenseSlipQuerySupport {

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDispenseRefundSupersedeLoader medicationDispenseRefundSupersedeLoader;

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
        query.setResolvedSupersededForwardMedReqIds(medicationDispenseRefundSupersedeLoader.listSupersededForwardMedReqIds());
    }

    private void enrichSupersededForwardMedReqIds(MedicationDispenseSlipPageQuery query) {
        if (query == null || !this.slipQueryIncludesCompletedStatus(query)) {
            return;
        }
        query.setResolvedSupersededForwardMedReqIds(medicationDispenseRefundSupersedeLoader.listSupersededForwardMedReqIds());
    }

    private boolean slipQueryIncludesCompletedStatus(MedicationDispenseSlipPageQuery query) {
        if (StringUtils.isNotBlank(query.getStatusEnum())
            && DispenseStatus.COMPLETED.getCode().equals(query.getStatusEnum().trim())) {
            return true;
        }
        return this.includesCompletedStatus(query.getDispenseStatusEnumIn());
    }

    private boolean includesCompletedStatus(List<String> dispenseStatusEnumIn) {
        if (dispenseStatusEnumIn == null || dispenseStatusEnumIn.isEmpty()) {
            return false;
        }
        for (String code : dispenseStatusEnumIn) {
            if (StringUtils.isBlank(code)) {
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
     */
    private void enrichEncounterIdsForKeyword(MedicationDispenseSlipPageQuery query) {
        if (StringUtils.isBlank(query.getKeyword()) || query.getEncounterId() != null) {
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
