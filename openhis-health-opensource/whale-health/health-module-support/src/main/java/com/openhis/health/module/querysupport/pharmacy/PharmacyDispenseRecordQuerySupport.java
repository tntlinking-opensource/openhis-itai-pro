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

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 发退药记录查询：跨聚合关键字与场景预解析（B.6.1.3）。
 *
 * <p>将药品/患者关键字、项目类型、门诊/住院场景收窄写入 {@link MedicationDispenseRecordPageQuery} resolved 字段，
 * 发放域 QueryService 仅 IN 下推。</p>
 */
@Component
@RequiredArgsConstructor
public class PharmacyDispenseRecordQuerySupport {

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /**
     * 门诊发退药记录：写入场景标志与 resolved 收窄字段。
     *
     * @param query                    发放域记录查询入参（原地修改）
     * @param medicationCategoryCodes  项目类型多选（可为 null）
     * @param drugKeyword              药品关键字（可为 null）
     * @param patientKeyword           患者关键字（可为 null）
     */
    public void enrichOutpatientRecordFilters(
        MedicationDispenseRecordPageQuery query,
        List<String> medicationCategoryCodes,
        String drugKeyword,
        String patientKeyword) {
        if (query == null) {
            return;
        }
        // 正向行仅门诊药品场景（与住院筛互斥）
        query.setOutpatientMedicationOnly(Boolean.TRUE);
        query.setInpatientMedicationOnly(null);
        this.enrichMedicationCategoryFilter(query, medicationCategoryCodes);
        this.enrichDrugKeywordFilter(query, drugKeyword);
        this.enrichOutpatientPatientKeywordFilter(query, patientKeyword);
    }

    /**
     * 住院发退药记录：写入场景标志与 resolved 收窄字段。
     *
     * @param query                    发放域记录查询入参（原地修改）
     * @param medicationCategoryCodes  项目类型多选（可为 null）
     * @param drugKeyword              药品关键字（可为 null）
     * @param patientKeyword           患者关键字（可为 null）
     */
    public void enrichInpatientRecordFilters(
        MedicationDispenseRecordPageQuery query,
        List<String> medicationCategoryCodes,
        String drugKeyword,
        String patientKeyword) {
        if (query == null) {
            return;
        }
        // 正向行仅住院药品场景
        query.setInpatientMedicationOnly(Boolean.TRUE);
        query.setOutpatientMedicationOnly(null);
        this.enrichMedicationCategoryFilter(query, medicationCategoryCodes);
        this.enrichDrugKeywordFilter(query, drugKeyword);
        this.enrichInpatientPatientKeywordFilter(query, patientKeyword);
    }

    private void enrichMedicationCategoryFilter(
        MedicationDispenseRecordPageQuery query,
        List<String> medicationCategoryCodes) {
        if (medicationCategoryCodes == null || medicationCategoryCodes.isEmpty()) {
            return;
        }
        List<Long> medicationIds =
            this.medicationDefinitionQueryService.listIdsByCategoryCodesIn(medicationCategoryCodes);
        query.setResolvedMedicationIdsForCategory(medicationIds);
    }

    private void enrichDrugKeywordFilter(MedicationDispenseRecordPageQuery query, String drugKeyword) {
        if (StringUtils.isBlank(drugKeyword)) {
            return;
        }
        ProductDefinitionCatalogPageQuery catalogQuery = new ProductDefinitionCatalogPageQuery();
        catalogQuery.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        catalogQuery.setKeyword(drugKeyword.trim());
        catalogQuery.setPageNo(1);
        catalogQuery.setPageSize(PharmacyDispenseRecordConstants.DRUG_KEYWORD_RESOLVE_PAGE_SIZE);
        Page<MedicationDefinition> catalogPage = this.medicationDefinitionQueryService.pageForCatalog(
            catalogQuery,
            PageRequest.of(0, PharmacyDispenseRecordConstants.DRUG_KEYWORD_RESOLVE_PAGE_SIZE));
        List<Long> medicationIds = new ArrayList<>();
        for (MedicationDefinition definition : catalogPage.getContent()) {
            if (definition != null && definition.getId() != null) {
                medicationIds.add(definition.getId());
            }
        }
        query.setResolvedMedicationIdsForDrugKeyword(medicationIds);
    }

    private void enrichOutpatientPatientKeywordFilter(
        MedicationDispenseRecordPageQuery query,
        String patientKeyword) {
        if (StringUtils.isBlank(patientKeyword)) {
            return;
        }
        List<Long> ambEncounterIds = this.resolveOutpatientAmbEncounterIdsByKeyword(patientKeyword.trim());
        query.setResolvedEncounterIdsForPatientKeyword(ambEncounterIds);
        // 退药行须与门诊就诊同窗（DM-MED-002 §6.2.18）
        query.setResolvedEncounterIdsForScene(ambEncounterIds);
    }

    private void enrichInpatientPatientKeywordFilter(
        MedicationDispenseRecordPageQuery query,
        String patientKeyword) {
        if (StringUtils.isBlank(patientKeyword)) {
            return;
        }
        List<Long> impEncounterIds = this.resolveInpatientImpEncounterIdsByKeyword(patientKeyword.trim());
        query.setResolvedEncounterIdsForPatientKeyword(impEncounterIds);
        query.setResolvedEncounterIdsForScene(impEncounterIds);
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
