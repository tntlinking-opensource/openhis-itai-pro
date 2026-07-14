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
     * @return 完整病证组列表；仅一侧的异常组不返回
     */
    public List<TcmDiagnosisGroupRowDto> groupTcmDiagnosisRows(Long encounterId, String diagnosisJson) {
        List<TcmEncounterDiagnosisAssociationQueryModel> associations =
            this.encounterQueryService.listTcmEncounterDiagnosisAssociations(encounterId);
        if (associations.isEmpty()) {
            return List.of();
        }
        Map<Long, DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rowByConditionId = new HashMap<>();
        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row
            : this.diagnosisJsonSnapshotParser.parseStrict(diagnosisJson)) {
            if (row.getConditionId() != null
                && DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(row.getDiagnosisKind())) {
                rowByConditionId.put(row.getConditionId(), row);
            }
        }
        List<Long> definitionIds = new ArrayList<>();
        for (DiagnosisJsonSnapshotParser.ParsedDiagnosisRow row : rowByConditionId.values()) {
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
            TcmDiagnosisGroupRowDto groupRow = this.buildGroupRow(entry.getKey(), entry.getValue(), rowByConditionId, definitionById);
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
        Map<Long, DiagnosisJsonSnapshotParser.ParsedDiagnosisRow> rowByConditionId,
        Map<Long, DoctorStationConditionDefinitionRowModel> definitionById
    ) {
        TcmDiagnosisGroupRowDto dto = new TcmDiagnosisGroupRowDto();
        dto.setTcmSyndromeGroup(groupKey);
        boolean hasDisease = false;
        boolean hasSyndrome = false;
        for (TcmEncounterDiagnosisAssociationQueryModel member : members) {
            DiagnosisJsonSnapshotParser.ParsedDiagnosisRow snapshotRow = rowByConditionId.get(member.getConditionId());
            if (snapshotRow == null || snapshotRow.getConditionDefinitionId() == null) {
                continue;
            }
            DoctorStationConditionDefinitionRowModel definition =
                definitionById.get(snapshotRow.getConditionDefinitionId());
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
            } else if (StringUtils.isNotEmpty(snapshotRow.getDiagnosisDesc())) {
                dto.setDiagnosisDesc(snapshotRow.getDiagnosisDesc());
            }
            if (DoctorStationTcmConstants.TCM_SOURCE_DISEASE.equals(definition.getSourceEnum())) {
                dto.setDiseaseConditionId(member.getConditionId());
                dto.setDiseaseDefinitionId(definition.getId());
                dto.setDiseaseCode(definition.getConditionCode());
                dto.setDiseaseName(definition.getName());
                hasDisease = true;
            }
            if (DoctorStationTcmConstants.TCM_SOURCE_SYNDROME.equals(definition.getSourceEnum())) {
                dto.setSyndromeConditionId(member.getConditionId());
                dto.setSyndromeDefinitionId(definition.getId());
                dto.setSyndromeCode(definition.getConditionCode());
                dto.setSyndromeName(definition.getName());
                hasSyndrome = true;
            }
        }
        if (!hasDisease || !hasSyndrome) {
            return null;
        }
        return dto;
    }
}
