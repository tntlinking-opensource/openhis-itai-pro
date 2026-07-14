/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.diagnosis;

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
import com.openhis.whale.core.application.util.StringUtils;
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

    /** 就诊读侧：加载 adm 就诊诊断关联。 */
    private final EncounterQueryService encounterQueryService;
    /** 病情事实读侧：批量读 condition 与合成关联。 */
    private final ConditionQueryService conditionQueryService;
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
     * <p>经 Encounter / Condition / ConditionDefinition 三个 QS 批量读（B.6.4 跨聚合读侧编排）。</p>
     *
     * @param encounterId 就诊主键；null 时返回空数组快照
     * @return 快照 JSON 与主诊断名
     */
    public DiagnosisSnapshot buildSnapshot(Long encounterId) {
        // Gate：无就诊主键时返回空数组，避免下游 NPE
        if (encounterId == null) {
            return new DiagnosisSnapshot(DoctorStationDiagnosisConstants.DIAGNOSIS_SNAPSHOT_EMPTY_ARRAY, null);
        }
        // Pre：加载就诊诊断关联（adm 优先，无关联时从 condition 事实合成）
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
        // Pre：经 Condition QS 批量读病情事实（B.6.4）
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
            String diagnosisKind = DoctorStationDiagnosisConstants.resolveDiagnosisKindFromSource(sourceForKind);
            row.put(FieldNameConstants.DIAGNOSIS_KIND, diagnosisKind);
            row.put(FieldNameConstants.DIAGNOSIS_KIND_TEXT, DiagnosisKindPresentationUtil.toDisplay(diagnosisKind));
            row.put(FieldNameConstants.VERIFICATION_STATUS_ENUM, verificationStatusToUiCode(c.getVerificationStatusEnum()));
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
            String desc = ed.getDescription();
            if (StringUtils.isEmpty(desc)) {
                desc = c.getDescription();
            }
            row.put(FieldNameConstants.DIAGNOSIS_DESC, desc);
            if (StringUtils.isNotEmpty(ed.getDiagTypeCode())) {
                row.put(FieldNameConstants.IPT_DISEASE_TYPE_CODE, ed.getDiagTypeCode());
            }
            if (StringUtils.isNotEmpty(ed.getTcmSyndromeGroup())) {
                row.put(FieldNameConstants.TCM_SYNDROME_GROUP, ed.getTcmSyndromeGroup());
            }
            if (def != null && StringUtils.isNotEmpty(def.getSourceEnum())) {
                row.put(FieldNameConstants.SOURCE_ENUM, def.getSourceEnum());
            }
            row.put(FieldNameConstants.YB_NO, c.getYbNo());
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

    /**
     * 解析就诊有效诊断关联：adm 关联表优先，无行时从 condition 事实合成。
     *
     * @param encounterId 就诊主键
     * @return 有效诊断关联列表
     */
    private List<EncounterDiagnosisQueryModel> resolveActiveAssociations(Long encounterId) {
        // Pre：经 Encounter QS 读 adm 就诊诊断关联（B.6.4）
        List<EncounterDiagnosisQueryModel> active = new ArrayList<>(
                encounterQueryService.listEncounterDiagnoses(encounterId));
        if (!active.isEmpty()) {
            return active;
        }
        // Pre：adm 无关联时经 Condition QS 反查事实并合成关联行（B.6.4）
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
            // 合成关联：首条事实视为主诊断
            if (rank == 1) {
                model.setPrimaryFlag(Boolean.TRUE);
            } else {
                model.setPrimaryFlag(Boolean.FALSE);
            }
            model.setRank(rank);
            synthesized.add(model);
            rank++;
        }
        return synthesized;
    }

    /**
     * FHIR verification_status code → 医生站 UI 整数码。
     *
     * @param verificationStatusEnum 持久化验证状态码
     * @return UI 码；空输入默认 CONFIRMED
     */
    private int verificationStatusToUiCode(String verificationStatusEnum) {
        if (StringUtils.isEmpty(verificationStatusEnum)) {
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
}
