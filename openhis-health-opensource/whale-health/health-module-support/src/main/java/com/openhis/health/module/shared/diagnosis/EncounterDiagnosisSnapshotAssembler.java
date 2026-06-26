/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.shared.diagnosis;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.DiagnosisKindPresentationUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bridge 共享：就诊诊断快照读侧组装（经各领域 {@code *QueryService} 批量读，拼装 {@code diagnosisJson}）。
 */
@Component("integrationEncounterDiagnosisSnapshotAssembler")
@RequiredArgsConstructor
public class EncounterDiagnosisSnapshotAssembler {

    private final EncounterQueryService encounterQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    public record DiagnosisSnapshot(String diagnosisJson, String conditionDefinitionName) {
    }

    public DiagnosisSnapshot buildSnapshot(Long encounterId) {
        if (encounterId == null) {
            return new DiagnosisSnapshot(DoctorStationDiagnosisConstants.DIAGNOSIS_SNAPSHOT_EMPTY_ARRAY, null);
        }
        List<EncounterDiagnosisQueryModel> active = resolveActiveAssociations(encounterId);
        active.sort(Comparator
                .comparing(EncounterDiagnosisQueryModel::getRank, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EncounterDiagnosisQueryModel::getDiagnosisId, Comparator.nullsLast(Comparator.naturalOrder())));
        if (active.isEmpty()) {
            return new DiagnosisSnapshot(DoctorStationDiagnosisConstants.DIAGNOSIS_SNAPSHOT_EMPTY_ARRAY, null);
        }
        List<Long> conditionIds = new ArrayList<>();
        for (EncounterDiagnosisQueryModel ed : active) {
            if (ed.getConditionId() != null) {
                conditionIds.add(ed.getConditionId());
            }
        }
        Map<Long, ConditionBatchItemQueryModel> condById = new HashMap<>();
        for (ConditionBatchItemQueryModel c : conditionQueryService.getConditionListById(conditionIds)) {
            condById.put(c.getId(), c);
        }
        Set<Long> defIdSet = new LinkedHashSet<>();
        for (ConditionBatchItemQueryModel c : condById.values()) {
            if (c.getDefinitionId() != null) {
                defIdSet.add(c.getDefinitionId());
            }
        }
        Map<Long, ConditionDefinitionBatchItemQueryModel> defById = new HashMap<>();
        if (!defIdSet.isEmpty()) {
            List<Long> defIds = new ArrayList<>(defIdSet);
            for (ConditionDefinitionBatchItemQueryModel def
                    : conditionDefinitionQueryService.getConditionDefinitionListByIds(defIds)) {
                defById.put(def.getId(), def);
            }
        }
        JSONArray array = new JSONArray();
        String primaryName = null;
        for (EncounterDiagnosisQueryModel ed : active) {
            ConditionBatchItemQueryModel c = condById.get(ed.getConditionId());
            if (c == null) {
                continue;
            }
            ConditionDefinitionBatchItemQueryModel def = null;
            if (c.getDefinitionId() != null) {
                def = defById.get(c.getDefinitionId());
            }
            JSONObject row = new JSONObject();
            row.put(FieldNameConstants.ENCOUNTER_DIAGNOSIS_ID, ed.getDiagnosisId());
            row.put(FieldNameConstants.CONDITION_ID, c.getId());
            row.put(FieldNameConstants.CONDITION_DEFINITION_ID, c.getDefinitionId());
            if (def != null) {
                row.put(FieldNameConstants.CONDITION_CODE, def.getConditionCode());
                row.put(FieldNameConstants.CONDITION_DEFINITION_NAME, def.getName());
            } else {
                row.put(FieldNameConstants.CONDITION_CODE, null);
                row.put(FieldNameConstants.CONDITION_DEFINITION_NAME, null);
            }
            String sourceForKind = null;
            if (def != null) {
                sourceForKind = def.getSourceEnum();
            }
            String diagnosisKind = resolveDiagnosisKind(sourceForKind);
            row.put(FieldNameConstants.DIAGNOSIS_KIND, diagnosisKind);
            row.put(FieldNameConstants.DIAGNOSIS_KIND_TEXT, DiagnosisKindPresentationUtil.toDisplay(diagnosisKind));
            row.put(FieldNameConstants.VERIFICATION_STATUS_ENUM, verificationStatusToUiCode(c.getVerificationStatusEnum()));
            int mainDiagnosisFlagValue;
            if (Boolean.TRUE.equals(ed.getPrimaryFlag())) {
                mainDiagnosisFlagValue = DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES;
            } else {
                mainDiagnosisFlagValue = DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_NO;
            }
            row.put(FieldNameConstants.MAIN_DIAGNOSIS_FLAG, mainDiagnosisFlagValue);
            row.put(FieldNameConstants.DIAGNOSIS_SORT_NO, ed.getRank());
            row.put(FieldNameConstants.MEDICAL_TYPE_CODE, ed.getMedType());
            String desc = ed.getDescription();
            if (StringUtils.isBlank(desc)) {
                desc = c.getDescription();
            }
            row.put(FieldNameConstants.DIAGNOSIS_DESC, desc);
            if (StringUtils.isNotBlank(ed.getDiagTypeCode())) {
                row.put(FieldNameConstants.IPT_DISEASE_TYPE_CODE, ed.getDiagTypeCode());
            }
            if (StringUtils.isNotBlank(ed.getTcmSyndromeGroup())) {
                row.put(FieldNameConstants.TCM_SYNDROME_GROUP, ed.getTcmSyndromeGroup());
            }
            if (def != null && StringUtils.isNotBlank(def.getSourceEnum())) {
                row.put(FieldNameConstants.SOURCE_ENUM, def.getSourceEnum());
            }
            row.put(FieldNameConstants.YB_NO, c.getYbNo());
            array.add(row);
            if (Boolean.TRUE.equals(ed.getPrimaryFlag()) && def != null && primaryName == null) {
                primaryName = def.getName();
            }
        }
        if (primaryName == null && !array.isEmpty()) {
            JSONObject first = array.getJSONObject(0);
            String n = first.getString(FieldNameConstants.CONDITION_DEFINITION_NAME);
            if (StringUtils.isNotBlank(n)) {
                primaryName = n;
            }
        }
        return new DiagnosisSnapshot(array.toJSONString(), primaryName);
    }

    private List<EncounterDiagnosisQueryModel> resolveActiveAssociations(Long encounterId) {
        List<EncounterDiagnosisQueryModel> active = new ArrayList<>(
                encounterQueryService.listEncounterDiagnoses(encounterId));
        if (!active.isEmpty()) {
            return active;
        }
        List<com.openhis.health.domain.clinical.condition.queries.model.EncounterDiagnosisQueryModel> facts =
                conditionQueryService.getEncounterDiagnosis(encounterId);
        if (facts.isEmpty()) {
            return active;
        }
        List<EncounterDiagnosisQueryModel> synthesized = new ArrayList<>(facts.size());
        int rank = 1;
        for (com.openhis.health.domain.clinical.condition.queries.model.EncounterDiagnosisQueryModel fact : facts) {
            if (fact.getConditionId() == null) {
                continue;
            }
            EncounterDiagnosisQueryModel model = new EncounterDiagnosisQueryModel();
            model.setEncounterId(encounterId);
            model.setConditionId(fact.getConditionId());
            model.setDescription(fact.getDescription());
            model.setPrimaryFlag(rank == 1 ? Boolean.TRUE : Boolean.FALSE);
            model.setRank(rank);
            synthesized.add(model);
            rank++;
        }
        return synthesized;
    }

    private int verificationStatusToUiCode(String verificationStatusEnum) {
        if (StringUtils.isBlank(verificationStatusEnum)) {
            return DoctorStationDiagnosisConstants.VerificationUiCode.CONFIRMED;
        }
        return switch (verificationStatusEnum.trim()) {
            case DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.UNCONFIRMED ->
                    DoctorStationDiagnosisConstants.VerificationUiCode.UNCONFIRMED;
            case DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.PROVISIONAL ->
                    DoctorStationDiagnosisConstants.VerificationUiCode.PROVISIONAL;
            case DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.DIFFERENTIAL ->
                    DoctorStationDiagnosisConstants.VerificationUiCode.DIFFERENTIAL;
            case DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.CONFIRMED ->
                    DoctorStationDiagnosisConstants.VerificationUiCode.CONFIRMED;
            case DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.REFUTED ->
                    DoctorStationDiagnosisConstants.VerificationUiCode.REFUTED;
            case DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.ENTERED_IN_ERROR ->
                    DoctorStationDiagnosisConstants.VerificationUiCode.ENTERED_IN_ERROR;
            default -> DoctorStationDiagnosisConstants.VerificationUiCode.CONFIRMED;
        };
    }

    private String resolveDiagnosisKind(String sourceEnumRaw) {
        return DoctorStationDiagnosisConstants.resolveDiagnosisKindFromSource(sourceEnumRaw);
    }
}
