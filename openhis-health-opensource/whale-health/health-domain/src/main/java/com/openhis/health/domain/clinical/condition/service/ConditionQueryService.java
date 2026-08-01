/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.condition.service;


import com.openhis.health.domain.clinical.condition.entity.Condition;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.clinical.condition.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.clinical.condition.repository.ConditionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * 诊断（Condition）事实读侧查询服务。
 *
 * <p><b>DD 退役</b>：禁止业务调用；读路径改经 {@code adm_encounter_diagnosis} + {@code ConditionDefinitionQueryService}。</p>
 *
 * @deprecated DD 退役，禁止业务调用
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class ConditionQueryService {

    /** 病情事实（Condition）仓储：按 id/就诊等派生查询，条件在方法名与底座软删中体现。 */
    private final ConditionRepository repository;

    /**
     * 按主键集合批量查询诊断记录。
     *
     * <p><b>规模（B.2）</b>：依赖仓储 {@code IN} 查询；若 {@code ids} 体量极大，需由调用方分批或在库侧评估索引与 SQL 长度上限。</p>
     *
     * @param ids 诊断记录主键集合
     * @return 命中列表；ids 为空时返回空集合
     */
    public List<ConditionBatchItemQueryModel> getConditionListById(List<Long> ids) {
        // 无 id 时不访问仓储，保持批量接口空入参安全。
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        // 单次 IN 查询，满足 A.9.1 批量读；不在循环内按 id 单条查。
        List<Condition> entities = repository.findByIdIn(ids);
        List<ConditionBatchItemQueryModel> models = new ArrayList<>();
        for (Condition entity : entities) {
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            // 装配为只读模型：仅暴露列表/卡片所需字段，避免把整实体泄漏到应用边界外。
            ConditionBatchItemQueryModel model = new ConditionBatchItemQueryModel();
            model.setId(entity.getId());
            model.setPatientId(entity.getPatientId());
            model.setDefinitionId(entity.getDefinitionId());
            model.setVerificationStatusEnum(entity.getVerificationStatusEnum());
            model.setClinicalStatusEnum(entity.getClinicalStatusEnum());
            model.setDescription(entity.getDescription());
            model.setYbNo(entity.getYbNo());
            models.add(model);
        }
        return models;
    }

    /**
     * 按就诊查询诊断记录。
     *
     * @param encounterId 就诊标识
     * @return 命中列表；encounterId 为空时返回空集合
     */
    public List<EncounterDiagnosisQueryModel> getEncounterDiagnosis(Long encounterId) {
        if (encounterId == null) {
            return new ArrayList<>();
        }
        // 就诊维度事实列表：由仓储派生方法下推 encounter 条件，不在内存再筛就诊。
        List<Condition> entities = repository.findByEncounterId(encounterId);
        List<EncounterDiagnosisQueryModel> models = new ArrayList<>();
        for (Condition entity : entities) {
            EncounterDiagnosisQueryModel model = new EncounterDiagnosisQueryModel();
            model.setConditionId(entity.getId());
            model.setEncounterId(entity.getEncounterId());
            model.setPatientId(entity.getPatientId());
            model.setDefinitionId(entity.getDefinitionId());
            model.setDescription(entity.getDescription());
            model.setRecordedDatetime(entity.getRecordedDatetime());
            models.add(model);
        }
        return models;
    }

    /**
     * 按患者加载历史诊断事实（医生站「病人历史诊断」快捷数据源）。
     *
     * @param patientId 患者主键
     * @return 诊断事实列表；{@code patientId} 为空时返回空集合
     */
    public List<Condition> findByPatientId(Long patientId) {
        if (patientId == null) {
            return new ArrayList<>();
        }
        return repository.findByPatientId(patientId);
    }

    /**
     * 按病情事实主键解析诊断目录 ID（只读，供跨聚合编排复用主诊病情用）。
     *
     * @param conditionId 病情事实主键
     * @return 诊断目录 ID；不存在或已软删时 {@code null}
     */
    public Long findDefinitionIdByConditionId(Long conditionId) {
        if (conditionId == null) {
            return null;
        }
        Optional<Condition> conditionOpt = repository.findById(conditionId);
        if (conditionOpt.isEmpty()) {
            return null;
        }
        Condition condition = conditionOpt.get();
        if (Boolean.TRUE.equals(condition.getIsDeleted())) {
            return null;
        }
        return condition.getDefinitionId();
    }

    /**
     * 统计指定诊断被医嘱引用的数量（删除前门控，见 BR-CND-003）。
     *
     * @param conditionId 诊断主键
     * @return 引用计数；{@code conditionId} 为空时返回 0
     */
    public int countAdviceRefByConditionId(Long conditionId) {
        if (conditionId == null) {
            return 0;
        }
        return repository.countAdviceRefByConditionId(conditionId);
    }
}
