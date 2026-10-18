/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.outpatient.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.outpatient.command.OpClinicDiagnosisLine;
import com.zxplat.plugin.chs.domain.share.constants.ChsOutpatientRegisterFeeConstants;
import com.zxplat.plugin.chs.domain.share.enums.DiagType;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 就诊诊断 → 国家医保诊断编码映射（{@code cln_condition_definition.yb_no}，不经 Condition）。
 *
 * <p>Wave B：支持全量临床诊断列表；未对码（无 yb_no）整单失败并提示。</p>
 */
public final class InsuranceDiagnosisCodeAssembler {

    private InsuranceDiagnosisCodeAssembler() {
    }

    /** 主诊编码/名称载体（兼容旧调用）。 */
    public record MainDiagnosis(String code, String name) {
    }

    /**
     * 解析主诊断（全量列表中的主诊行；无诊断时 code/name 为空）。
     *
     * @param encounterId                     就诊 id
     * @param encounterQueryService           就诊查询
     * @param conditionDefinitionQueryService 诊断定义查询
     * @return 主诊；无关联时 code/name 为 null
     */
    public static MainDiagnosis resolveMainDiagnosis(
        Long encounterId,
        EncounterQueryService encounterQueryService,
        ConditionDefinitionQueryService conditionDefinitionQueryService) {
        List<OpClinicDiagnosisLine> lines = resolveClinicDiagnoses(
            encounterId,
            encounterQueryService,
            conditionDefinitionQueryService,
            false);
        if (lines.isEmpty()) {
            return new MainDiagnosis(null, null);
        }
        OpClinicDiagnosisLine primary = lines.get(0);
        return new MainDiagnosis(primary.getDiagCode(), primary.getDiagName());
    }

    /**
     * 装载本诊次全部有效诊断（主诊优先，其余按 rank 升序），供 2203 diseinfo。
     *
     * <p>挂号/兼容路径可继续用；诊疗结算优先走 {@link #resolveClinicDiagnosesByOrderedIds}，
     * 以账单 {@code encounter_diagnosis_ids} 为上传真源。</p>
     *
     * @param encounterId                     就诊 id
     * @param encounterQueryService           就诊查询
     * @param conditionDefinitionQueryService 诊断定义查询
     * @param requireNonEmpty                 true 时无诊断抛业务异常（诊疗结算）
     * @return 诊断行列表；requireNonEmpty=false 且无关联时返回空列表
     */
    public static List<OpClinicDiagnosisLine> resolveClinicDiagnoses(
        Long encounterId,
        EncounterQueryService encounterQueryService,
        ConditionDefinitionQueryService conditionDefinitionQueryService,
        boolean requireNonEmpty) {
        // Pre：读就诊诊断关联
        List<EncounterDiagnosisQueryModel> associations =
            encounterQueryService.listEncounterDiagnoses(encounterId);
        if (associations == null || associations.isEmpty()) {
            if (requireNonEmpty) {
                throw new BusinessException(
                    "INS-012",
                    "诊疗费医保结算须先在医生站下达诊断，请下诊断后再收费");
            }
            return List.of();
        }
        // Pre：主诊优先，其余按 rank（空 rank 靠后），稳定次序
        List<EncounterDiagnosisQueryModel> ordered = new ArrayList<>(associations);
        ordered.sort(Comparator
            .comparing((EncounterDiagnosisQueryModel row) -> !Boolean.TRUE.equals(row.getPrimaryFlag()))
            .thenComparing(
                EncounterDiagnosisQueryModel::getRank,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return toClinicDiagnosisLines(ordered, conditionDefinitionQueryService, requireNonEmpty);
    }

    /**
     * 按账单绑定的有序就诊诊断 id 装载 2203 diseinfo（多诊断全量上传）。
     *
     * <p>顺序与 ids 串一致：首位标记 {@code maindiag_flag=1}（便于统计/展示），
     * <b>不是</b>「结算只取主诊」——其后各位一并上传。</p>
     *
     * @param orderedEncounterDiagnosisIds    有序就诊诊断行主键（去重后仍保序）
     * @param encounterQueryService           就诊查询
     * @param conditionDefinitionQueryService 诊断定义查询
     * @param requireNonEmpty                 true 时无有效行抛业务异常
     * @return diseinfo 行列表
     */
    public static List<OpClinicDiagnosisLine> resolveClinicDiagnosesByOrderedIds(
        List<Long> orderedEncounterDiagnosisIds,
        EncounterQueryService encounterQueryService,
        ConditionDefinitionQueryService conditionDefinitionQueryService,
        boolean requireNonEmpty) {
        if (orderedEncounterDiagnosisIds == null || orderedEncounterDiagnosisIds.isEmpty()) {
            if (requireNonEmpty) {
                throw new BusinessException(
                    "INS-012",
                    "诊疗费医保结算须先在医生站下达诊断，请下诊断后再收费");
            }
            return List.of();
        }
        // Pre：按 id 批量加载就诊诊断行，再按入参顺序重排（保留 ids 串主诊首位）
        List<EncounterDiagnosisQueryModel> all =
            encounterQueryService.listEncounterDiagnosesByIds(orderedEncounterDiagnosisIds);
        Map<Long, EncounterDiagnosisQueryModel> byId = new HashMap<>(all.size() * 2);
        for (EncounterDiagnosisQueryModel row : all) {
            if (row != null && row.getDiagnosisId() != null) {
                byId.put(row.getDiagnosisId(), row);
            }
        }
        List<EncounterDiagnosisQueryModel> ordered = new ArrayList<>(orderedEncounterDiagnosisIds.size());
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        for (Long id : orderedEncounterDiagnosisIds) {
            if (id == null || !seen.add(id)) {
                continue;
            }
            EncounterDiagnosisQueryModel row = byId.get(id);
            if (row != null) {
                ordered.add(row);
            }
        }
        return toClinicDiagnosisLines(ordered, conditionDefinitionQueryService, requireNonEmpty);
    }

    /**
     * 将已排序的就诊诊断关联映射为平台 diseinfo 行（须 yb_no；首位 maindiag_flag=1）。
     *
     * @param ordered                         有序关联行
     * @param conditionDefinitionQueryService 目录查询
     * @param requireNonEmpty                 无有效行是否抛错
     * @return 临床诊断行
     */
    private static List<OpClinicDiagnosisLine> toClinicDiagnosisLines(
        List<EncounterDiagnosisQueryModel> ordered,
        ConditionDefinitionQueryService conditionDefinitionQueryService,
        boolean requireNonEmpty) {
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (EncounterDiagnosisQueryModel row : ordered) {
            if (row != null && row.getConditionDefinitionId() != null) {
                definitionIds.add(row.getConditionDefinitionId());
            }
        }
        if (definitionIds.isEmpty()) {
            if (requireNonEmpty) {
                throw new BusinessException(
                    "INS-012",
                    "诊疗费医保结算须先在医生站下达诊断，请下诊断后再收费");
            }
            return List.of();
        }
        Map<Long, ConditionDefinitionBatchItemQueryModel> definitionById = new HashMap<>();
        if (conditionDefinitionQueryService != null) {
            List<ConditionDefinitionBatchItemQueryModel> definitions =
                conditionDefinitionQueryService.getConditionDefinitionListByIds(
                    new ArrayList<>(definitionIds));
            for (ConditionDefinitionBatchItemQueryModel definition : definitions) {
                if (definition != null && definition.getId() != null) {
                    definitionById.put(definition.getId(), definition);
                }
            }
        }

        List<OpClinicDiagnosisLine> lines = new ArrayList<>();
        int srtNo = 1;
        for (EncounterDiagnosisQueryModel association : ordered) {
            if (association == null || association.getConditionDefinitionId() == null) {
                continue;
            }
            ConditionDefinitionBatchItemQueryModel definition =
                definitionById.get(association.getConditionDefinitionId());
            String ybNo = null;
            String name = null;
            if (definition != null) {
                // 仅认目录 yb_no，禁止国临/院内 condition_code 回落冒充医保码
                ybNo = resolveNationalDiagCode(definition.getYbNo(), definition.getConditionCode());
                if (isNotEmpty(definition.getName())) {
                    name = definition.getName().trim();
                }
            }
            if (isEmpty(name) && isNotEmpty(association.getDescription())) {
                name = association.getDescription().trim();
            }
            String hint = name;
            if (isEmpty(hint)) {
                hint = association.getDescription();
            }
            if (isEmpty(hint)) {
                hint = String.valueOf(association.getConditionDefinitionId());
            }
            if (isEmpty(ybNo) || isEmpty(name) || "-".equals(ybNo.trim())) {
                throw new BusinessException(
                    "INS-012",
                    "诊断未对码（缺少国家医保诊断编码 yb_no），请先完成目录对照后再收费：" + hint);
            }
            // Gate：医保疾病目录已停用/灰码不可上传
            if (conditionDefinitionQueryService != null
                && conditionDefinitionQueryService.isInactiveGeneralDxInsuranceCode(ybNo)) {
                throw new BusinessException(
                    "INS-012",
                    "诊断医保码已停用或为灰码，请改选有效诊断：" + hint + "（" + ybNo.trim() + "）");
            }
            // 首位固定主诊标记（ids 串协议 / 统计口径）；其余一律次诊——上传仍含全部行
            String mainFlag;
            if (srtNo == 1) {
                mainFlag = ChsOutpatientRegisterFeeConstants.DIAG_MAINDIAG_FLAG_YES;
            } else {
                mainFlag = "0";
            }
            OpClinicDiagnosisLine line = new OpClinicDiagnosisLine()
                .setDiagSrtNo(srtNo)
                .setDiagCode(ybNo.trim())
                .setDiagName(name.trim())
                .setDiagType(resolveDiagTypeCode(association))
                .setMaindiagFlag(mainFlag);
            lines.add(line);
            srtNo++;
        }
        if (lines.isEmpty() && requireNonEmpty) {
            throw new BusinessException(
                "INS-012",
                "诊疗费医保结算须先在医生站下达诊断，请下诊断后再收费");
        }
        return lines;
    }

    /**
     * 将就诊关联的中西医标记映射为平台 diag_type。
     *
     * @param association 就诊诊断关联
     * @return 平台诊断类别码
     */
    public static String resolveDiagTypeCode(EncounterDiagnosisQueryModel association) {
        if (association != null && Boolean.TRUE.equals(association.getTcmFlag())) {
            return DiagType.TCM_MAIN.getCode();
        }
        return DiagType.WESTERN.getCode();
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

    /**
     * 解析主诊对应诊断定义 id。
     *
     * @param encounterId           就诊 id
     * @param encounterQueryService 就诊查询
     * @return 诊断定义 id；无主诊时 null
     */
    public static Long resolvePrimaryDefinitionId(
        Long encounterId,
        EncounterQueryService encounterQueryService) {
        List<EncounterDiagnosisQueryModel> associations =
            encounterQueryService.listEncounterDiagnoses(encounterId);
        if (associations.isEmpty()) {
            return null;
        }
        EncounterDiagnosisQueryModel chosen = associations.stream()
            .filter(row -> Boolean.TRUE.equals(row.getPrimaryFlag()))
            .findFirst()
            .orElseGet(() -> associations.stream()
                .min(Comparator.comparing(
                    EncounterDiagnosisQueryModel::getRank,
                    Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(associations.get(0)));
        return chosen.getConditionDefinitionId();
    }

    /**
     * 解析国家医保诊断编码：仅接受目录 {@code yb_no}。
     *
     * <p>{@code conditionCode} 保留入参兼容旧调用，<strong>不再</strong>作为医保码回落，
     * 避免国临灰码/未对码行直接上传省平台。</p>
     *
     * @param ybNo          目录医保码
     * @param conditionCode 目录主码（忽略，勿作上传码）
     * @return trim 后的医保码；无则 null
     */
    public static String resolveNationalDiagCode(String ybNo, String conditionCode) {
        if (isNotEmpty(ybNo)) {
            return ybNo.trim();
        }
        return null;
    }
}
