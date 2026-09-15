/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.diagnosis;

import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 由就诊诊断定义推导「编码 → 目录来源」提示，供病案首页/结算清单区分国临与医保同码。
 *
 * <p>同码既在国临又在医保目录时，以医生实际开立的定义来源为准；
 * 西医医保来源可覆盖同码国临提示，避免医保码被误塞进国临列。</p>
 */
@Component
@RequiredArgsConstructor
public class EncounterDiagnosisSourceHintResolver {

    private final EncounterQueryService encounterQueryService;

    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /**
     * 按就诊诊断定义批量解析编码来源提示。
     *
     * @param encounterId 就诊主键；可空
     * @return 诊断编码 → {@code source_enum}；无数据返回空 Map
     */
    public Map<String, String> resolveSourceHintByCode(Long encounterId) {
        Map<String, String> result = new LinkedHashMap<>();
        // Gate：无就诊则无法反查开立来源
        if (encounterId == null) {
            return result;
        }
        // Pre：装载就诊诊断关联
        List<EncounterDiagnosisQueryModel> associations =
                encounterQueryService.listEncounterDiagnoses(encounterId);
        if (associations == null || associations.isEmpty()) {
            return result;
        }
        Set<Long> defIds = new LinkedHashSet<>();
        for (EncounterDiagnosisQueryModel row : associations) {
            // 跳过未绑目录定义的行
            if (row != null && row.getConditionDefinitionId() != null) {
                defIds.add(row.getConditionDefinitionId());
            }
        }
        if (defIds.isEmpty()) {
            return result;
        }
        // Pre：批量读目录定义（本聚合 ConditionDefinition）
        List<ConditionDefinitionBatchItemQueryModel> defs =
                conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(defIds));
        if (defs == null || defs.isEmpty()) {
            return result;
        }
        // Asm：编码 → source_enum；同码冲突时医保西医覆盖国临
        for (ConditionDefinitionBatchItemQueryModel def : defs) {
            if (def == null) {
                continue;
            }
            String source = trimToNull(def.getSourceEnum());
            if (source == null) {
                continue;
            }
            String conditionCode = trimToNull(def.getConditionCode());
            if (conditionCode != null) {
                this.putSourceHint(result, conditionCode, source);
            }
            String ybNo = trimToNull(def.getYbNo());
            // 医保目录 yb_no 与主码不同时一并挂提示，便于按落库码命中
            if (ybNo != null && !ybNo.equals(conditionCode)) {
                this.putSourceHint(result, ybNo, source);
            }
        }
        return result;
    }

    /**
     * 写入来源提示；同码冲突时西医医保覆盖国临。
     *
     * @param target 结果 Map
     * @param code   诊断编码
     * @param source 目录来源
     */
    private void putSourceHint(Map<String, String> target, String code, String source) {
        String existing = target.get(code);
        // 业务分支：首次写入直接落盘
        if (existing == null) {
            target.put(code, source);
            return;
        }
        // 业务分支：已有国临、新来西医医保 → 以医保为准（医生实际开立）
        if (DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(existing)
                && DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_WESTERN.contains(source)) {
            target.put(code, source);
        }
        // 其余冲突：保留先写入来源，避免国临覆盖已认定的医保提示
    }
}
