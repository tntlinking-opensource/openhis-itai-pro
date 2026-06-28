/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement.support;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * adapter 侧：就诊主诊断 → 国家医保病种编码（{@code cln_condition_definition.yb_no}）。
 *
 * <p>供 {@link InpatientInsuranceContextLoader}、{@link OutpatientInsuranceContextLoader}、
 * {@link OutpatientInsuranceChargeGrouper} 共用；禁止在 chs-jilin-application 重复查 HIS 诊断目录。</p>
 */
public final class InsuranceDiagnosisCodeSupport {

    private InsuranceDiagnosisCodeSupport() {
    }

    /** 主诊断编码与名称（平台 dise_codg / dscg_maindiag_code 来源）。 */
    public record MainDiagnosis(String code, String name) {
    }

    /**
     * 解析就诊主诊断，用于 2401/220x {@code mainDiagCode}。
     *
     * @param encounterId 就诊主键
     * @param encounterQueryService 就诊诊断关联读侧
     * @param conditionQueryService 病情事实读侧
     * @param conditionDefinitionQueryService 诊断目录读侧
     * @return 国家医保编码优先；无诊断时 code/name 可为 null
     */
    public static MainDiagnosis resolveMainDiagnosis(
        Long encounterId,
        EncounterQueryService encounterQueryService,
        ConditionQueryService conditionQueryService,
        ConditionDefinitionQueryService conditionDefinitionQueryService) {
        // Pre：就诊主诊断关联 → 诊断目录 definitionId
        Long definitionId = resolvePrimaryDefinitionId(encounterId, encounterQueryService, conditionQueryService);
        if (definitionId == null) {
            return new MainDiagnosis(null, null);
        }
        // Cmd：目录 yb_no → 平台病种编码
        String code = resolveNationalDiagCodeForDefinitionId(definitionId, conditionDefinitionQueryService);
        String name = resolveDefinitionNameForDefinitionId(definitionId, conditionDefinitionQueryService, null);
        return new MainDiagnosis(code, name);
    }

    /**
     * 按诊断目录 ID 解析国家医保病种编码（2203/2204 dise_codg、分组键）。
     *
     * @param definitionId {@code cln_condition_definition.id}
     * @param conditionDefinitionQueryService 诊断目录读侧
     * @return 优先 {@code yb_no}；无则回退院内 {@code condition_code}
     */
    public static String resolveNationalDiagCodeForDefinitionId(
        Long definitionId,
        ConditionDefinitionQueryService conditionDefinitionQueryService) {
        if (definitionId == null || conditionDefinitionQueryService == null) {
            return null;
        }
        // Pre：批量读目录（含 yb_no，不限发布状态）
        List<ConditionDefinitionBatchItemQueryModel> batch =
            conditionDefinitionQueryService.getConditionDefinitionListByIds(List.of(definitionId));
        if (batch.isEmpty()) {
            return null;
        }
        ConditionDefinitionBatchItemQueryModel item = batch.get(0);
        return resolveNationalDiagCode(item.getYbNo(), item.getConditionCode());
    }

    /**
     * 按诊断目录 ID 解析展示名称。
     */
    public static String resolveDefinitionNameForDefinitionId(
        Long definitionId,
        ConditionDefinitionQueryService conditionDefinitionQueryService,
        String fallbackDescription) {
        if (definitionId == null || conditionDefinitionQueryService == null) {
            return fallbackDescription;
        }
        List<ConditionDefinitionBatchItemQueryModel> batch =
            conditionDefinitionQueryService.getConditionDefinitionListByIds(List.of(definitionId));
        if (!batch.isEmpty() && StringUtils.isNotBlank(batch.get(0).getName())) {
            return batch.get(0).getName().trim();
        }
        return fallbackDescription;
    }

    /**
     * 从就诊诊断关联解析 UI 所选主诊断的目录 ID。
     */
    public static Long resolvePrimaryDefinitionId(
        Long encounterId,
        EncounterQueryService encounterQueryService,
        ConditionQueryService conditionQueryService) {
        List<com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel> associations =
            encounterQueryService.listEncounterDiagnoses(encounterId);
        if (associations.isEmpty()) {
            return null;
        }
        // Pre：primaryFlag 优先，否则按 rank 取第一条
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

    /**
     * 平台病种编码：优先国家医保 {@code yb_no}，院内 {@code condition_code} 仅兜底。
     */
    public static String resolveNationalDiagCode(String ybNo, String conditionCode) {
        if (StringUtils.isNotBlank(ybNo)) {
            return ybNo.trim();
        }
        if (StringUtils.isNotBlank(conditionCode)) {
            return conditionCode.trim();
        }
        return null;
    }
}
