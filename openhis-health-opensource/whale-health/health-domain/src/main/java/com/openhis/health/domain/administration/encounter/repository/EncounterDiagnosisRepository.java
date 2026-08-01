/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.repository;

import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 就诊诊断关联仓储：读写 {@code adm_encounter_diagnosis}，直挂 {@code condition_definition_id}。
 */
public interface EncounterDiagnosisRepository extends JpaRepository<EncounterDiagnosis, Long> {

    /**
     * 按主键集合批量加载就诊诊断关联行。
     *
     * @param ids 关联表主键集合
     * @return 实体列表
     */
    List<EncounterDiagnosis> findAllByIdIn(Collection<Long> ids);

    /**
     * 查询就诊下全部诊断关联（含已软删行，调用方须自行过滤）。
     *
     * @param encounterId 就诊标识
     * @return 诊断关联列表
     */
    List<EncounterDiagnosis> findAllByEncounterId(Long encounterId);

    /**
     * 查询就诊下主诊断记录。
     *
     * @param encounterId 就诊标识
     * @return 主诊断记录
     */
    Optional<EncounterDiagnosis> findFirstByEncounterIdAndPrimaryFlagTrue(Long encounterId);

    /**
     * 按就诊与诊断目录定义查询西医关联行（同就诊同定义通常唯一）。
     *
     * @param encounterId 就诊标识
     * @param conditionDefinitionId 诊断目录主键
     * @return 诊断关联记录
     */
    Optional<EncounterDiagnosis> findByEncounterIdAndConditionDefinitionId(Long encounterId, Long conditionDefinitionId);

    /**
     * 按诊断目录主键批量加载就诊诊断关联（患者历史病证组反查）。
     *
     * @param conditionDefinitionIds 诊断目录主键集合
     * @return 关联行列表；入参为空时由调用方保证不入库
     */
    List<EncounterDiagnosis> findAllByConditionDefinitionIdIn(Collection<Long> conditionDefinitionIds);

    /**
     * 按医保医疗类别与就诊主键集合批量加载就诊诊断关联行（含软删，调用方过滤）。
     *
     * @param medType      {@code med_type} 持久化 code（{@code YbMedType#getCode()}）
     * @param encounterIds 就诊主键集合
     * @return 关联行列表
     */
    List<EncounterDiagnosis> findAllByMedTypeAndEncounterIdIn(String medType, Collection<Long> encounterIds);

    /**
     * 按就诊主键集合批量加载诊断关联行（报表 med_type 展示 enrich；含软删，调用方过滤）。
     *
     * @param encounterIds 就诊主键集合
     * @return 关联行列表
     */
    List<EncounterDiagnosis> findAllByEncounterIdIn(Collection<Long> encounterIds);
}
