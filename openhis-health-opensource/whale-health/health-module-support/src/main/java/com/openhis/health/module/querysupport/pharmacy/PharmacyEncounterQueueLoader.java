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
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyReturnEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.OutpatientPharmacyPendingEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 药房发药/退药患者队列：跨聚合读编排（B.6.1.3），预解析就诊收窄后委托 {@link MedicationDispenseQueryService}。
 */
@Component
@RequiredArgsConstructor
public class PharmacyEncounterQueueLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final LocationQueryService locationQueryService;
    private final PharmacyDispenseSlipQuerySupport pharmacyDispenseSlipQuerySupport;

    /**
     * 门诊发药队列：分页返回存在门诊药品发放事实的就诊主键。
     *
     * @param query 库房、发放状态、关键字等；{@code null} 时按空条件
     * @return 就诊主键页（按发放创建时间倒序）
     */
    public Page<Long> pageEncounterIdsForOutpatientPharmacyPending(OutpatientPharmacyPendingEncounterPageQuery query) {
        if (query == null) {
            query = new OutpatientPharmacyPendingEncounterPageQuery();
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            query.setResolvedEncounterScope(this.resolveOutpatientAmbEncounterIdsByKeyword(query.getKeyword()));
        }
        this.pharmacyDispenseSlipQuerySupport.enrichSupersededForwardMedReqIds(query);
        return this.medicationDispenseQueryService.pageEncounterIdsForOutpatientPharmacyPending(query);
    }

    /**
     * 住院发药队列：分页返回存在药品发放事实且住院在院的就诊主键。
     *
     * @param query 库房、病区、关键字等
     * @return 就诊主键页
     */
    public Page<Long> pageEncounterIdsForInpatientPharmacyPending(InpatientPharmacyPendingEncounterPageQuery query) {
        if (query == null) {
            query = new InpatientPharmacyPendingEncounterPageQuery();
        }
        List<Long> bedIdsUnderWard = null;
        if (query.getWardId() != null) {
            bedIdsUnderWard = this.locationQueryService.listActiveBedIdsUnderWard(query.getWardId());
        }
        List<Long> scope = this.encounterQueryService.listInpatientInHospitalEncounterIds(
            query.getWardId(), bedIdsUnderWard);
        if (StringUtils.isNotBlank(query.getKeyword())) {
            scope = this.intersectEncounterIds(scope, this.resolveInpatientEncounterIdsByKeyword(query.getKeyword()));
        }
        query.setResolvedEncounterScope(scope);
        return this.medicationDispenseQueryService.pageEncounterIdsForInpatientPharmacyPending(query);
    }

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
        if (query.getDepartmentId() != null || StringUtils.isNotBlank(query.getKeyword())) {
            query.setResolvedEncounterScope(
                this.resolveOutpatientReturnEncounterScope(query.getDepartmentId(), query.getKeyword()));
        }
        return this.medicationDispenseQueryService.pageEncounterIdsForOutpatientPharmacyReturn(query);
    }

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
        if (StringUtils.isNotBlank(query.getKeyword())) {
            scope = this.intersectEncounterIds(scope, this.resolveInpatientEncounterIdsByKeyword(query.getKeyword()));
        }
        query.setResolvedEncounterScope(scope);
        return this.medicationDispenseQueryService.pageEncounterIdsForInpatientPharmacyReturn(query);
    }

    private List<Long> resolveOutpatientReturnEncounterScope(Long departmentId, String keyword) {
        LinkedHashSet<Long> scope = new LinkedHashSet<>();
        if (departmentId != null) {
            scope.addAll(this.encounterQueryService.listOutpatientAmbEncounterIdsByOrganization(departmentId));
        }
        if (StringUtils.isNotBlank(keyword)) {
            List<Long> keywordHits = this.resolveOutpatientAmbEncounterIdsByKeyword(keyword);
            if (departmentId != null) {
                return this.intersectEncounterIds(new ArrayList<>(scope), keywordHits);
            }
            return keywordHits;
        }
        return new ArrayList<>(scope);
    }

    private List<Long> resolveOutpatientAmbEncounterIdsByKeyword(String keyword) {
        LinkedHashSet<Long> union = new LinkedHashSet<>();
        union.addAll(this.encounterQueryService.filterOutpatientAmbEncounterIds(
            this.encounterQueryService.listEncounterIdsByBusNoKeyword(keyword)));
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
        union.addAll(this.encounterQueryService.filterOutpatientAmbEncounterIds(
            this.encounterQueryService.listEncounterIdsByPatientIds(patientIds)));
        return new ArrayList<>(union);
    }

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
