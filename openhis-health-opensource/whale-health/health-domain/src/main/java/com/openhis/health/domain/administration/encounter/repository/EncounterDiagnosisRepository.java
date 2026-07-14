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
 * 就诊诊断关联仓储。
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
     * 查询就诊下全部未删除诊断关联。
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
     * 按就诊和诊断标识查询记录。
     *
     * @param encounterId 就诊标识
     * @param conditionId 诊断标识
     * @return 诊断关联记录
     */
    Optional<EncounterDiagnosis> findByEncounterIdAndConditionId(Long encounterId, Long conditionId);

    /**
     * 按病情事实主键批量加载就诊诊断关联（患者历史病证组反查）。
     *
     * @param conditionIds 病情事实主键集合
     * @return 关联行列表；入参为空时由调用方保证不入库
     */
    List<EncounterDiagnosis> findAllByConditionIdIn(Collection<Long> conditionIds);
}
