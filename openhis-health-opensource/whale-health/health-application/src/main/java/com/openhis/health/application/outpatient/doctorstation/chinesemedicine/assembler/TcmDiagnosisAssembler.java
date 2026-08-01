/*

 * Copyright © 2026 OpenHIS Contributors

 * SPDX-License-Identifier: LGPL-3.0-or-later

 */

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.assembler;



import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmDiagnosisGroupRowDto;

import com.openhis.health.application.shared.diagnosis.DiagnosisJsonSnapshotParser;

import com.openhis.health.domain.administration.encounter.queries.model.TcmEncounterDiagnosisAssociationQueryModel;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;

import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;

import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;

import com.openhis.health.domain.share.constants.DoctorStationTcmConstants;

import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;

import lombok.RequiredArgsConstructor;

import com.openhis.whale.core.application.util.StringUtils;

import org.springframework.stereotype.Component;



import java.util.ArrayList;

import java.util.HashMap;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;



/**

 * IF-DOR-30：中医病证组读侧聚合（按 {@code tcm_syndrome_group} 一病一证成组）。

 */

@Component

@RequiredArgsConstructor

public class TcmDiagnosisAssembler {



    private final EncounterQueryService encounterQueryService;

    private final DiagnosisJsonSnapshotParser diagnosisJsonSnapshotParser;

    private final ConditionDefinitionQueryService conditionDefinitionQueryService;



    /**

     * 将就诊中医关联与诊断快照 JSON 聚合为病证组展示行。

     *

     * @param encounterId   就诊主键

     * @param diagnosisJson 就诊诊断快照 JSON 真源

     * @return 病证组列表；病、证皆无的空组不返回（允许仅病或仅证）

     */

    public List<TcmDiagnosisGroupRowDto> groupTcmDiagnosisRows(Long encounterId, String diagnosisJson) {

        List<TcmEncounterDiagnosisAssociationQueryModel> associations =

            this.encounterQueryService.listTcmEncounterDiagnosisAssociations(encounterId);

        if (associations.isEmpty()) {

            return List.of();

        }

        Map<Long, DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rowByEncounterDiagnosisId = new HashMap<>();

        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row

            : this.diagnosisJsonSnapshotParser.parseStrict(diagnosisJson)) {

            if (row.getEncounterDiagnosisId() != null

                && DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(row.getDiagnosisKind())) {

                rowByEncounterDiagnosisId.put(row.getEncounterDiagnosisId(), row);

            }

        }

        List<Long> definitionIds = new ArrayList<>();

        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rowByEncounterDiagnosisId.values()) {

            if (row.getConditionDefinitionId() != null) {

                definitionIds.add(row.getConditionDefinitionId());

            }

        }

        Map<Long, DoctorStationConditionDefinitionRowModel> definitionById = new HashMap<>();

        List<DoctorStationConditionDefinitionRowModel> definitions =

            this.conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(definitionIds);

        for (DoctorStationConditionDefinitionRowModel definition : definitions) {

            definitionById.put(definition.getId(), definition);

        }

        Map<String, List<TcmEncounterDiagnosisAssociationQueryModel>> grouped = new LinkedHashMap<>();

        for (TcmEncounterDiagnosisAssociationQueryModel association : associations) {

            if (StringUtils.isEmpty(association.getTcmSyndromeGroup())) {

                continue;

            }

            String groupKey = association.getTcmSyndromeGroup().trim();

            List<TcmEncounterDiagnosisAssociationQueryModel> bucket = grouped.get(groupKey);

            if (bucket == null) {

                bucket = new ArrayList<>();

                grouped.put(groupKey, bucket);

            }

            bucket.add(association);

        }

        List<TcmDiagnosisGroupRowDto> result = new ArrayList<>();

        for (Map.Entry<String, List<TcmEncounterDiagnosisAssociationQueryModel>> entry : grouped.entrySet()) {

            TcmDiagnosisGroupRowDto groupRow = this.buildGroupRow(

                entry.getKey(), entry.getValue(), rowByEncounterDiagnosisId, definitionById);

            if (groupRow != null) {

                result.add(groupRow);

            }

        }

        return result;

    }



    /**

     * 装配单个病证组行：必须同时解析出病名（source=05）与证候（source=06）。

     */

    private TcmDiagnosisGroupRowDto buildGroupRow(

        String groupKey,

        List<TcmEncounterDiagnosisAssociationQueryModel> members,

        Map<Long, DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rowByEncounterDiagnosisId,

        Map<Long, DoctorStationConditionDefinitionRowModel> definitionById

    ) {

        TcmDiagnosisGroupRowDto dto = new TcmDiagnosisGroupRowDto();

        dto.setTcmSyndromeGroup(groupKey);

        boolean hasDisease = false;

        boolean hasSyndrome = false;

        for (TcmEncounterDiagnosisAssociationQueryModel member : members) {

            DiagnosisJsonSnapshotParser.ParsedDiagnosisRow snapshotRow =

                rowByEncounterDiagnosisId.get(member.getEncounterDiagnosisId());

            Long definitionId = member.getConditionDefinitionId();

            if (definitionId == null && snapshotRow != null) {

                definitionId = snapshotRow.getConditionDefinitionId();

            }

            if (definitionId == null) {

                continue;

            }

            DoctorStationConditionDefinitionRowModel definition = definitionById.get(definitionId);

            if (definition == null) {

                continue;

            }

            if (member.getPrimaryFlag() != null) {

                if (Boolean.TRUE.equals(member.getPrimaryFlag())) {

                    dto.setPrimaryFlag(DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES);

                } else {

                    dto.setPrimaryFlag(DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_NO);

                }

            }

            if (member.getRank() != null) {

                dto.setRank(member.getRank());

            }

            dto.setMedicalTypeCode(member.getMedType());

            if (StringUtils.isNotEmpty(member.getDescription())) {

                dto.setDiagnosisDesc(member.getDescription());

            } else if (snapshotRow != null && StringUtils.isNotEmpty(snapshotRow.getDiagnosisDesc())) {

                dto.setDiagnosisDesc(snapshotRow.getDiagnosisDesc());

            }

            if (DoctorStationTcmConstants.TCM_SOURCE_DISEASE.equals(definition.getSourceEnum())) {

                dto.setDiseaseEncounterDiagnosisId(member.getEncounterDiagnosisId());

                dto.setDiseaseDefinitionId(definition.getId());

                dto.setDiseaseCode(definition.getConditionCode());

                dto.setDiseaseName(definition.getName());

                dto.setYbNo(definition.getYbNo());

                hasDisease = true;

            }

            if (DoctorStationTcmConstants.TCM_SOURCE_SYNDROME.equals(definition.getSourceEnum())) {

                dto.setSyndromeEncounterDiagnosisId(member.getEncounterDiagnosisId());

                dto.setSyndromeDefinitionId(definition.getId());

                dto.setSyndromeCode(definition.getConditionCode());

                dto.setSyndromeName(definition.getName());

                hasSyndrome = true;

            }

        }

        // 允许仅病或仅证成组回显（院点放宽一病一证后读侧须可见）
        if (!hasDisease && !hasSyndrome) {

            return null;

        }

        return dto;

    }

}

