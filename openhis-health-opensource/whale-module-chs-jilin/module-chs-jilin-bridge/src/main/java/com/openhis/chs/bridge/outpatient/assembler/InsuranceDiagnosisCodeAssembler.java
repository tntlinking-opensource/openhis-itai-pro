/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;

import java.util.Comparator;
import java.util.List;

/**
 * 就诊主诊断 → 国家医保病种编码映射（{@code cln_condition_definition.yb_no}）。
 *
 * <p>读 health-domain 诊断定义；纯内存转换，无 IO。</p>
 */
public final class InsuranceDiagnosisCodeAssembler {

    private InsuranceDiagnosisCodeAssembler() {
    }

    public record MainDiagnosis(String code, String name) {
    }

    public static MainDiagnosis resolveMainDiagnosis(
        Long encounterId,
        EncounterQueryService encounterQueryService,
        ConditionQueryService conditionQueryService,
        ConditionDefinitionQueryService conditionDefinitionQueryService) {
        Long definitionId = resolvePrimaryDefinitionId(encounterId, encounterQueryService, conditionQueryService);
        if (definitionId == null) {
            return new MainDiagnosis(null, null);
        }
        String code = resolveNationalDiagCodeForDefinitionId(definitionId, conditionDefinitionQueryService);
        String name = resolveDefinitionNameForDefinitionId(definitionId, conditionDefinitionQueryService, null);
        return new MainDiagnosis(code, name);
    }

    public static String resolveNationalDiagCodeForDefinitionId(
        Long definitionId,
        ConditionDefinitionQueryService conditionDefinitionQueryService) {
        if (definitionId == null || conditionDefinitionQueryService == null) {
            return null;
        }
        List<ConditionDefinitionBatchItemQueryModel> batch =
            conditionDefinitionQueryService.getConditionDefinitionListByIds(List.of(definitionId));
        if (batch.isEmpty()) {
            return null;
        }
        ConditionDefinitionBatchItemQueryModel item = batch.get(0);
        return resolveNationalDiagCode(item.getYbNo(), item.getConditionCode());
    }

    public static String resolveDefinitionNameForDefinitionId(
        Long definitionId,
        ConditionDefinitionQueryService conditionDefinitionQueryService,
        String fallbackDescription) {
        if (definitionId == null || conditionDefinitionQueryService == null) {
            return fallbackDescription;
        }
        List<ConditionDefinitionBatchItemQueryModel> batch =
            conditionDefinitionQueryService.getConditionDefinitionListByIds(List.of(definitionId));
        if (!batch.isEmpty() && isNotEmpty(batch.get(0).getName())) {
            return batch.get(0).getName().trim();
        }
        return fallbackDescription;
    }

    public static Long resolvePrimaryDefinitionId(
        Long encounterId,
        EncounterQueryService encounterQueryService,
        ConditionQueryService conditionQueryService) {
        List<com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel> associations =
            encounterQueryService.listEncounterDiagnoses(encounterId);
        if (associations.isEmpty()) {
            return null;
        }
        com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel chosen =
            associations.stream()
            .filter(row -> Boolean.TRUE.equals(row.getPrimaryFlag()))
            .findFirst()
            .orElseGet(() -> associations.stream()
                .min(Comparator.comparing(
                    com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel::getRank,
                    Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(associations.get(0)));
        if (chosen.getConditionId() == null) {
            return null;
        }
        return conditionQueryService.findDefinitionIdByConditionId(chosen.getConditionId());
    }

    public static String resolveNationalDiagCode(String ybNo, String conditionCode) {
        if (isNotEmpty(ybNo)) {
            return ybNo.trim();
        }
        if (isNotEmpty(conditionCode)) {
            return conditionCode.trim();
        }
        return null;
    }
}
