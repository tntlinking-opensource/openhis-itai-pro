/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispenserecord.support;

import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.zxplat.domain.share.constants.PharmacyDispenseRecordConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.query.ProductDefinitionCatalogPageQuery;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
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
public class PharmacyDispenseRecordLoader {

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

    /**
     * 按重点药/药品性质类型收窄 {@code resolvedMedicationIdsForCategory}（与类型多选求交）。
     *
     * <p>空筛选跳过；未知码写入空列表（拒查）。用于发药报表「药品性质」筛麻醉/精一/精二。</p>
     *
     * @param query       发放域记录查询入参（原地修改）
     * @param keyDrugType {@link com.zxplat.domain.share.enums.KeyDrugType} code；空=不限
     */
    public void enrichKeyDrugTypeFilter(MedicationDispenseRecordPageQuery query, String keyDrugType) {
        if (query == null || StringUtils.isEmpty(keyDrugType)) {
            return;
        }
        List<Long> byKeyDrug =
            this.medicationDefinitionQueryService.listIdsByKeyDrugType(keyDrugType.trim());
        List<Long> existing = query.getResolvedMedicationIdsForCategory();
        // 尚无类型预筛：直接写入重点药主键
        if (existing == null) {
            query.setResolvedMedicationIdsForCategory(byKeyDrug);
            return;
        }
        // 已有类型 IN：与重点药求交
        if (existing.isEmpty() || byKeyDrug == null || byKeyDrug.isEmpty()) {
            query.setResolvedMedicationIdsForCategory(List.of());
            return;
        }
        LinkedHashSet<Long> keyDrugSet = new LinkedHashSet<>(byKeyDrug);
        List<Long> intersected = new ArrayList<>();
        for (Long medicationId : existing) {
            if (medicationId != null && keyDrugSet.contains(medicationId)) {
                intersected.add(medicationId);
            }
        }
        query.setResolvedMedicationIdsForCategory(intersected);
    }

    /**
     * 按药品类别 code 解析 medicationId 列表并写入查询预筛字段。
     *
     * @param query                   发药记录分页查询
     * @param medicationCategoryCodes 药品类别 code 列表；空则跳过
     */
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

    /**
     * 按药品关键字分页检索目录并写入 medicationId 预筛列表。
     *
     * @param query       发药记录分页查询
     * @param drugKeyword 药品名称/编码关键字；空则跳过
     */
    private void enrichDrugKeywordFilter(MedicationDispenseRecordPageQuery query, String drugKeyword) {
        if (StringUtils.isEmpty(drugKeyword)) {
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

    /**
     * 门诊发药记录：按患者关键字解析 amb 就诊 id 并写入查询预筛（含退药同窗约束）。
     *
     * @param query          发药记录分页查询
     * @param patientKeyword 患者姓名/就诊号等关键字；空则跳过
     */
    private void enrichOutpatientPatientKeywordFilter(
        MedicationDispenseRecordPageQuery query,
        String patientKeyword) {
        if (StringUtils.isEmpty(patientKeyword)) {
            return;
        }
        List<Long> ambEncounterIds = this.resolveOutpatientAmbEncounterIdsByKeyword(patientKeyword.trim());
        query.setResolvedEncounterIdsForPatientKeyword(ambEncounterIds);
        // 退药行须与门诊就诊同窗（DM-MED-002 §6.2.18）
        query.setResolvedEncounterIdsForScene(ambEncounterIds);
    }

    /**
     * 住院发药记录：按患者关键字解析 imp 就诊 id 并写入查询预筛。
     *
     * @param query          发药记录分页查询
     * @param patientKeyword 患者姓名/就诊号等关键字；空则跳过
     */
    private void enrichInpatientPatientKeywordFilter(
        MedicationDispenseRecordPageQuery query,
        String patientKeyword) {
        if (StringUtils.isEmpty(patientKeyword)) {
            return;
        }
        List<Long> impEncounterIds = this.resolveInpatientImpEncounterIdsByKeyword(patientKeyword.trim());
        query.setResolvedEncounterIdsForPatientKeyword(impEncounterIds);
        query.setResolvedEncounterIdsForScene(impEncounterIds);
    }

    /**
     * 门诊患者关键字 → amb 就诊 id：就诊 busNo 与患者列表关键字并集去重。
     *
     * @param keyword 已 trim 的关键字
     * @return 可见 amb encounterId 列表；无命中时可能为空列表
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
     * 住院患者关键字 → imp 就诊 id：就诊 busNo 与患者列表关键字并集去重。
     *
     * @param keyword 已 trim 的关键字
     * @return 可见 imp encounterId 列表；无命中时可能为空列表
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
     * 从就诊实体列表提取非空 id（保持插入顺序）。
     *
     * @param encounters 就诊实体列表；null 视为空
     * @return encounterId 列表
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
