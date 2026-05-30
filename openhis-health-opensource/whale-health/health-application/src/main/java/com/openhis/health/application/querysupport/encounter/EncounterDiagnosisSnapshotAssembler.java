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

package com.openhis.health.application.querysupport.encounter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.mapper.DoctorStationDiagnosisMapper;
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
 * 就诊诊断快照读侧组装：经各领域 {@code *QueryService} 批量读，拼装内部 {@code diagnosisJson}，
 * 供 {@link OutpatientEncounterContextLoader}、{@link InpatientEncounterContextLoader} 与医生站解析使用。
 *
 * <p>对外 HTTP 契约仍须 Mapper 解析为结构化 DTO（B.6.2），禁止原样透出 JSON 串。</p>
 */
@Component
@RequiredArgsConstructor
public class EncounterDiagnosisSnapshotAssembler {

    private final EncounterQueryService encounterQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final DoctorStationDiagnosisMapper doctorStationDiagnosisMapper;

    /**
     * 就诊诊断快照（JSON 数组 + 主诊断名称摘要）。
     *
     * @param diagnosisJson 医生站 {@code diagnosisJson} 数组字符串；无诊断时为 {@link DoctorStationDiagnosisConstants#DIAGNOSIS_SNAPSHOT_EMPTY_ARRAY}
     * @param conditionDefinitionName 主诊断定义展示名；无主诊时可为首行定义名
     */
    public record DiagnosisSnapshot(String diagnosisJson, String conditionDefinitionName) {
    }

    /**
     * 按就诊实时拼装诊断行（IF 出参结构化 DTO，禁止经上下文 {@code diagnosisJson} 中转，见迁移文档 §四）。
     *
     * @param encounterId 就诊标识
     * @return 非 null 列表；无诊断时为空列表
     */
    public List<EncounterDiagnosisRowDto> buildRows(Long encounterId) {
        DiagnosisSnapshot snapshot = this.buildSnapshot(encounterId);
        return this.doctorStationDiagnosisMapper.parseDiagnosisJson(snapshot.diagnosisJson());
    }

    /**
     * 基于当前库表拼装医生站诊断快照，与 {@code DoctorStationDiagnosisMapperImpl#parseDiagnosisJson} 字段口径一致。
     *
     * @param encounterId 就诊标识
     * @return 非 null；无有效关联时为「空数组」快照且主诊断名为 null
     */
    public DiagnosisSnapshot buildSnapshot(Long encounterId) {
        if (encounterId == null) {
            return new DiagnosisSnapshot(DoctorStationDiagnosisConstants.DIAGNOSIS_SNAPSHOT_EMPTY_ARRAY, null);
        }
        List<EncounterDiagnosisQueryModel> active = this.resolveActiveAssociations(encounterId);
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
        for (ConditionBatchItemQueryModel c : this.conditionQueryService.getConditionListById(conditionIds)) {
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
                : this.conditionDefinitionQueryService.getConditionDefinitionListByIds(defIds)) {
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
            String diagnosisKind = this.resolveDiagnosisKind(sourceForKind);
            row.put(FieldNameConstants.DIAGNOSIS_KIND, diagnosisKind);
            row.put(FieldNameConstants.DIAGNOSIS_KIND_TEXT, DiagnosisKindPresentationUtil.toDisplay(diagnosisKind));
            row.put(FieldNameConstants.VERIFICATION_STATUS_ENUM, this.verificationStatusToUiCode(c.getVerificationStatusEnum()));
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

    /**
     * 解析就诊下活动诊断关联；关联子表无行时回退为按病情事实表（{@code Condition.encounterId}）合成视图。
     *
     * <p>读模型废止后存量住院线可能仅有病情事实、尚未维护 {@code adm_encounter_diagnosis}，须兼容回显。</p>
     *
     * @param encounterId 就诊标识
     * @return 非 null 关联视图列表
     */
    private List<EncounterDiagnosisQueryModel> resolveActiveAssociations(Long encounterId) {
        List<EncounterDiagnosisQueryModel> active = new ArrayList<>(
            this.encounterQueryService.listEncounterDiagnoses(encounterId));
        if (!active.isEmpty()) {
            return active;
        }
        // 关联子表无行：按就诊病情事实合成 rank/主诊，供快照与护士站列表展示。
        List<com.openhis.health.domain.clinical.condition.queries.model.EncounterDiagnosisQueryModel> facts =
            this.conditionQueryService.getEncounterDiagnosis(encounterId);
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
     * 病情核验状态持久化 code → 医生站 UI 整型码（仅展示用，非库表身份；见 {@link DoctorStationDiagnosisConstants.VerificationUiCode}）。
     */
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

    /**
     * 病情定义来源 → 中西医诊断种类标签。
     *
     * <p>DOC-CONFLICT：存量 {@code source_enum} 仍为数字串，此处仅用于展示分档；新字段须改为 code 身份（§0.12）。</p>
     */
    private String resolveDiagnosisKind(String sourceEnumRaw) {
        if (StringUtils.isBlank(sourceEnumRaw)) {
            return null;
        }
        Integer v;
        try {
            v = Integer.parseInt(sourceEnumRaw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        String asSourceCode = String.valueOf(v);
        if (DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_WESTERN.contains(asSourceCode)) {
            return DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_WESTERN;
        }
        if (DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_TCM.contains(asSourceCode)) {
            return DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM;
        }
        return null;
    }
}
