/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.bridge.metadata.context.diagnosis;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.utils.DiagnosisKindPresentationUtil;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bridge 共享：就诊诊断快照读侧组装（经 Encounter / ConditionDefinition QueryService 批量读，拼装 {@code diagnosisJson}）。
 */
@Component("integrationEmrEncounterDiagnosisSnapshotAssembler")
@RequiredArgsConstructor
public class EmrEncounterDiagnosisSnapshotAssembler {

    /** 就诊读侧：加载 adm 就诊诊断关联。 */
    private final EncounterQueryService encounterQueryService;

    /** 诊断目录读侧：批量读 condition_definition 展示名与来源。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /**
     * 就诊诊断快照（JSON 数组 + 主诊断展示名）。
     *
     * @param diagnosisJson 与医生站/护士站一致的快照 JSON
     * @param conditionDefinitionName 主诊断目录名；无有效行时为 null
     */
    public record DiagnosisSnapshot(String diagnosisJson, String conditionDefinitionName) {
    }

    /**
     * 按就诊主键组装诊断快照 JSON 与主诊断展示名。
     *
     * <p>经 Encounter / ConditionDefinition 两个 QS 批量读（B.6.4 跨聚合读侧编排）；不经 {@code cln_condition}。</p>
     *
     * @param encounterId 就诊主键；null 时返回空数组快照
     * @return 快照 JSON 与主诊断名
     */
    public DiagnosisSnapshot buildSnapshot(Long encounterId) {
        // Gate：无就诊主键时返回空数组，避免下游 NPE
        if (encounterId == null) {
            return new DiagnosisSnapshot(DoctorStationDiagnosisConstants.DIAGNOSIS_SNAPSHOT_EMPTY_ARRAY, null);
        }
        // Pre：加载就诊诊断关联（adm_encounter_diagnosis，直挂 condition_definition_id）
        List<EncounterDiagnosisQueryModel> active = new ArrayList<>(
            this.encounterQueryService.listEncounterDiagnoses(encounterId));
        active.sort(Comparator
            .comparing(EncounterDiagnosisQueryModel::getRank, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(EncounterDiagnosisQueryModel::getDiagnosisId, Comparator.nullsLast(Comparator.naturalOrder())));
        if (active.isEmpty()) {
            return new DiagnosisSnapshot(DoctorStationDiagnosisConstants.DIAGNOSIS_SNAPSHOT_EMPTY_ARRAY, null);
        }
        Set<Long> defIdSet = new LinkedHashSet<>();
        for (EncounterDiagnosisQueryModel ed : active) {
            if (ed.getConditionDefinitionId() != null) {
                defIdSet.add(ed.getConditionDefinitionId());
            }
        }
        // Pre：经 ConditionDefinition QS 批量读目录名与来源（B.6.4）
        Map<Long, ConditionDefinitionBatchItemQueryModel> defById = new HashMap<>();
        if (!defIdSet.isEmpty()) {
            List<Long> defIds = new ArrayList<>(defIdSet);
            for (ConditionDefinitionBatchItemQueryModel def
                    : conditionDefinitionQueryService.getConditionDefinitionListByIds(defIds)) {
                defById.put(def.getId(), def);
            }
        }
        // Asm：逐关联行拼装快照 JSON 数组元素
        JSONArray array = new JSONArray();
        String primaryName = null;
        for (EncounterDiagnosisQueryModel ed : active) {
            if (ed.getConditionDefinitionId() == null) {
                continue;
            }
            ConditionDefinitionBatchItemQueryModel def = defById.get(ed.getConditionDefinitionId());
            JSONObject row = new JSONObject();
            row.put(FieldNameConstants.ENCOUNTER_DIAGNOSIS_ID, ed.getDiagnosisId());
            row.put(FieldNameConstants.CONDITION_DEFINITION_ID, ed.getConditionDefinitionId());
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
            String diagnosisKind = DoctorStationDiagnosisConstants.resolveDiagnosisKindFromSource(sourceForKind);
            row.put(FieldNameConstants.DIAGNOSIS_KIND, diagnosisKind);
            row.put(FieldNameConstants.DIAGNOSIS_KIND_TEXT, DiagnosisKindPresentationUtil.toDisplay(diagnosisKind));
            row.put(FieldNameConstants.VERIFICATION_STATUS_ENUM, DoctorStationDiagnosisConstants.VerificationUiCode.CONFIRMED);
            int mainDiagnosisFlagValue;
            // 主诊断标记写入 UI 整数码（1=是，0=否）
            if (Boolean.TRUE.equals(ed.getPrimaryFlag())) {
                mainDiagnosisFlagValue = DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES;
            } else {
                mainDiagnosisFlagValue = DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_NO;
            }
            row.put(FieldNameConstants.MAIN_DIAGNOSIS_FLAG, mainDiagnosisFlagValue);
            row.put(FieldNameConstants.DIAGNOSIS_SORT_NO, ed.getRank());
            row.put(FieldNameConstants.MEDICAL_TYPE_CODE, ed.getMedType());
            row.put(FieldNameConstants.DIAGNOSIS_DESC, ed.getDescription());
            if (StringUtils.isNotEmpty(ed.getDiagTypeCode())) {
                row.put(FieldNameConstants.IPT_DISEASE_TYPE_CODE, ed.getDiagTypeCode());
            }
            if (StringUtils.isNotEmpty(ed.getTcmSyndromeGroup())) {
                row.put(FieldNameConstants.TCM_SYNDROME_GROUP, ed.getTcmSyndromeGroup());
            }
            if (def != null && StringUtils.isNotEmpty(def.getSourceEnum())) {
                row.put(FieldNameConstants.SOURCE_ENUM, def.getSourceEnum());
            }
            String ybNo = null;
            if (def != null) {
                ybNo = def.getYbNo();
            }
            row.put(FieldNameConstants.YB_NO, ybNo);
            array.add(row);
            if (Boolean.TRUE.equals(ed.getPrimaryFlag()) && def != null && primaryName == null) {
                primaryName = def.getName();
            }
        }
        if (primaryName == null && !array.isEmpty()) {
            // 无主诊断标记时回退首行目录名
            JSONObject first = array.getJSONObject(0);
            String n = first.getString(FieldNameConstants.CONDITION_DEFINITION_NAME);
            if (StringUtils.isNotEmpty(n)) {
                primaryName = n;
            }
        }
        return new DiagnosisSnapshot(array.toJSONString(), primaryName);
    }
}
