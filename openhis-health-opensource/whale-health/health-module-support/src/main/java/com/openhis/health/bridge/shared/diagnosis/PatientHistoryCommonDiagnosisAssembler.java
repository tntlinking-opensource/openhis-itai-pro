/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.bridge.shared.diagnosis;

import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.entity.Condition;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.DoctorStationTcmConstants;
import com.openhis.health.domain.share.utils.TcmDiagnosisPresentationUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-DOR-06A 患者历史分支：病情事实 + 就诊关联反查中医病证组，合并为快捷树候选行。
 */
@Component
@RequiredArgsConstructor
public class PatientHistoryCommonDiagnosisAssembler {

    private final ConditionQueryService conditionQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /**
     * 装配患者历史常用诊断候选（西医按定义去重；中医按病+证对去重合并展示）。
     *
     * @param patientId 患者主键
     * @return 历史候选行；无历史时返回空列表
     */
    public List<PatientHistoryCommonDiagnosisRow> assemble(Long patientId) {
        // Gate：无患者主键时不查库，避免全表扫描
        if (patientId == null) {
            return List.of();
        }
        // Pre：扫描患者全部病情事实，顺序保留首次出现（供后续装配保序）
        List<Condition> conditions = this.conditionQueryService.findByPatientId(patientId);
        if (conditions.isEmpty()) {
            return List.of();
        }
        // Pre：过滤软删事实，收集有效 conditionId 供就诊关联反查
        List<Long> activeConditionIds = new ArrayList<>();
        for (Condition condition : conditions) {
            if (condition == null || Boolean.TRUE.equals(condition.getIsDeleted())) {
                continue;
            }
            if (condition.getId() != null) {
                activeConditionIds.add(condition.getId());
            }
        }
        if (activeConditionIds.isEmpty()) {
            return List.of();
        }
        // Pre：反查就诊诊断关联，并按「就诊+中医组号」索引同组成员
        List<EncounterDiagnosisQueryModel> associations =
            this.encounterQueryService.listActiveAssociationsByConditionIds(activeConditionIds);
        Map<Long, EncounterDiagnosisQueryModel> associationByConditionId = new HashMap<>();
        Map<String, List<EncounterDiagnosisQueryModel>> membersByEncounterGroup = new HashMap<>();
        for (EncounterDiagnosisQueryModel association : associations) {
            if (association.getConditionId() == null) {
                continue;
            }
            associationByConditionId.put(association.getConditionId(), association);
            // 仅中医且带组号的关联参与病证组合并
            if (!Boolean.TRUE.equals(association.getTcmFlag())) {
                continue;
            }
            if (StringUtils.isBlank(association.getTcmSyndromeGroup())) {
                continue;
            }
            if (association.getEncounterId() == null) {
                continue;
            }
            String groupKey = this.buildEncounterGroupKey(
                association.getEncounterId(), association.getTcmSyndromeGroup());
            List<EncounterDiagnosisQueryModel> bucket = membersByEncounterGroup.get(groupKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                membersByEncounterGroup.put(groupKey, bucket);
            }
            bucket.add(association);
        }
        Set<String> processedEncounterGroups = new HashSet<>();
        Set<String> processedTcmPairs = new LinkedHashSet<>();
        Set<Long> processedWesternDefinitionIds = new LinkedHashSet<>();
        List<PatientHistoryCommonDiagnosisRow> rows = new ArrayList<>();
        // Asm：按事实出现顺序输出；中医同就诊组只出一行，西医按定义去重
        for (Condition condition : conditions) {
            if (condition == null || Boolean.TRUE.equals(condition.getIsDeleted())) {
                continue;
            }
            if (condition.getDefinitionId() == null) {
                continue;
            }
            EncounterDiagnosisQueryModel association = associationByConditionId.get(condition.getId());
            if (association != null
                && Boolean.TRUE.equals(association.getTcmFlag())
                && StringUtils.isNotBlank(association.getTcmSyndromeGroup())
                && association.getEncounterId() != null) {
                String encounterGroupKey = this.buildEncounterGroupKey(
                    association.getEncounterId(), association.getTcmSyndromeGroup());
                // 同就诊同组号已在本次遍历中输出过，跳过组内第二行（证候事实）
                if (processedEncounterGroups.contains(encounterGroupKey)) {
                    continue;
                }
                List<EncounterDiagnosisQueryModel> members = membersByEncounterGroup.get(encounterGroupKey);
                PatientHistoryCommonDiagnosisRow merged = this.buildTcmGroupRow(members, association.getTcmSyndromeGroup());
                if (merged != null) {
                    processedEncounterGroups.add(encounterGroupKey);
                    // 跨就诊按病+证定义对去重，避免历史树重复展示同一病证组
                    String pairKey = this.buildTcmPairKey(
                        merged.getConditionDefinitionId(), merged.getSyndromeDefinitionId());
                    if (processedTcmPairs.add(pairKey)) {
                        rows.add(merged);
                    }
                    continue;
                }
            }
            // 西医或未组成病证组：按 conditionDefinitionId 保序去重
            if (processedWesternDefinitionIds.add(condition.getDefinitionId())) {
                PatientHistoryCommonDiagnosisRow western = this.buildWesternRow(condition.getDefinitionId());
                if (western != null) {
                    rows.add(western);
                }
            }
        }
        return rows;
    }

    private String buildEncounterGroupKey(Long encounterId, String tcmSyndromeGroup) {
        return encounterId + "|" + tcmSyndromeGroup.trim();
    }

    private String buildTcmPairKey(Long diseaseDefinitionId, Long syndromeDefinitionId) {
        return String.valueOf(diseaseDefinitionId) + "|" + String.valueOf(syndromeDefinitionId);
    }

    private PatientHistoryCommonDiagnosisRow buildWesternRow(Long definitionId) {
        List<DoctorStationConditionDefinitionRowModel> definitions =
            this.conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(List.of(definitionId));
        if (definitions.isEmpty()) {
            return null;
        }
        DoctorStationConditionDefinitionRowModel definition = definitions.get(0);
        PatientHistoryCommonDiagnosisRow row = new PatientHistoryCommonDiagnosisRow();
        row.setConditionDefinitionId(definition.getId());
        row.setConditionCode(definition.getConditionCode());
        row.setConditionDefinitionName(definition.getName());
        row.setDiagnosisKind(DoctorStationDiagnosisConstants.resolveDiagnosisKindFromSource(definition.getSourceEnum()));
        row.setSourceEnumCode(definition.getSourceEnum());
        row.setSourceEnum(this.tryParseInt(definition.getSourceEnum()));
        row.setYbNo(definition.getYbNo());
        return row;
    }

    private PatientHistoryCommonDiagnosisRow buildTcmGroupRow(
        List<EncounterDiagnosisQueryModel> members,
        String tcmSyndromeGroup) {
        if (members == null || members.isEmpty()) {
            return null;
        }
        List<Long> conditionIds = new ArrayList<>();
        for (EncounterDiagnosisQueryModel member : members) {
            if (member.getConditionId() != null) {
                conditionIds.add(member.getConditionId());
            }
        }
        if (conditionIds.isEmpty()) {
            return null;
        }
        Map<Long, ConditionBatchItemQueryModel> conditionById = new HashMap<>();
        for (ConditionBatchItemQueryModel condition : this.conditionQueryService.getConditionListById(conditionIds)) {
            conditionById.put(condition.getId(), condition);
        }
        List<Long> definitionIds = new ArrayList<>();
        for (ConditionBatchItemQueryModel condition : conditionById.values()) {
            if (condition.getDefinitionId() != null) {
                definitionIds.add(condition.getDefinitionId());
            }
        }
        if (definitionIds.isEmpty()) {
            return null;
        }
        Map<Long, DoctorStationConditionDefinitionRowModel> definitionById = new HashMap<>();
        for (DoctorStationConditionDefinitionRowModel definition
            : this.conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(definitionIds)) {
            definitionById.put(definition.getId(), definition);
        }
        DoctorStationConditionDefinitionRowModel diseaseDefinition = null;
        DoctorStationConditionDefinitionRowModel syndromeDefinition = null;
        for (EncounterDiagnosisQueryModel member : members) {
            ConditionBatchItemQueryModel condition = conditionById.get(member.getConditionId());
            if (condition == null || condition.getDefinitionId() == null) {
                continue;
            }
            DoctorStationConditionDefinitionRowModel definition = definitionById.get(condition.getDefinitionId());
            if (definition == null) {
                continue;
            }
            // 按目录 source_enum 区分病名与证候定义
            if (DoctorStationTcmConstants.TCM_SOURCE_DISEASE.equals(definition.getSourceEnum())) {
                diseaseDefinition = definition;
            }
            if (DoctorStationTcmConstants.TCM_SOURCE_SYNDROME.equals(definition.getSourceEnum())) {
                syndromeDefinition = definition;
            }
        }
        // 病证组须同时解析出病名与证候，缺任一则不作为合并行返回
        if (diseaseDefinition == null || syndromeDefinition == null) {
            return null;
        }
        PatientHistoryCommonDiagnosisRow row = new PatientHistoryCommonDiagnosisRow();
        row.setConditionDefinitionId(diseaseDefinition.getId());
        row.setSyndromeDefinitionId(syndromeDefinition.getId());
        row.setConditionCode(diseaseDefinition.getConditionCode());
        row.setConditionDefinitionName(TcmDiagnosisPresentationUtil.formatGroupDisplayName(
            diseaseDefinition.getName(), syndromeDefinition.getName()));
        row.setDiagnosisKind(DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM);
        row.setSourceEnumCode(diseaseDefinition.getSourceEnum());
        row.setSourceEnum(this.tryParseInt(diseaseDefinition.getSourceEnum()));
        row.setYbNo(diseaseDefinition.getYbNo());
        row.setTcmSyndromeGroup(StringUtils.trimToNull(tcmSyndromeGroup));
        return row;
    }

    private Integer tryParseInt(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return null;
            }
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
