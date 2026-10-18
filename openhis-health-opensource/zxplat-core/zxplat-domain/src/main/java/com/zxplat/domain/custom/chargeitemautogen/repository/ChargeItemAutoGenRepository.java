/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargeitemautogen.repository;

import com.zxplat.domain.custom.chargeitemautogen.entity.ChargeItemAutoGen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 收费明细自动生成规则仓储。
 */
public interface ChargeItemAutoGenRepository extends JpaRepository<ChargeItemAutoGen, Long>, JpaSpecificationExecutor<ChargeItemAutoGen> {

    /**
     * 按主键加载规则行（含已软删，供框架默认对齐）。
     *
     * @param id 规则主键
     * @return 规则实体
     */
    Optional<ChargeItemAutoGen> findById(Long id);

    /**
     * 同一就诊 + 触发键 + 执行科室 + 定价组合是否已存在（BR-CAG-001）。
     */
    boolean existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionId(
        Long encounterId,
        String triggerSourceTable,
        Long triggerSourceId,
        Long performingOrgId,
        Long chargeDefinitionId
    );

    /**
     * 编辑场景：除当前规则外是否仍存在相同触发键组合（有效记录内唯一）。
     *
     * @param encounterId 就诊主键
     * @param triggerSourceTable 触发源表名
     * @param triggerSourceId 触发源主键
     * @param performingOrgId 执行科室
     * @param chargeDefinitionId 收费项目定义主键
     * @param id 当前规则主键（排除自身）
     * @return {@code true} 表示冲突
     */
    boolean existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionIdAndIdNot(
        Long encounterId,
        String triggerSourceTable,
        Long triggerSourceId,
        Long performingOrgId,
        Long chargeDefinitionId,
        Long id
    );

    /**
     * 按触发源查全部有效规则，按优先级与主键升序（医嘱/申请落费时匹配）。
     *
     * @param triggerSourceTable 触发源表名
     * @param triggerSourceId 触发源主键
     * @return 规则列表
     */
    List<ChargeItemAutoGen> findAllByTriggerSourceTableAndTriggerSourceIdAndIsDeletedFalseOrderByPriorityAscIdAsc(
        String triggerSourceTable,
        Long triggerSourceId
    );

    /**
     * 按就诊列出全部有效自动生成规则。
     *
     * @param encounterId 就诊主键
     * @return 规则列表
     */
    List<ChargeItemAutoGen> findAllByEncounterIdAndIsDeletedFalseOrderByPriorityAscIdAsc(Long encounterId);

    /**
     * 按收费项目定义反查关联规则（维护页联动）。
     *
     * @param chargeDefinitionId 收费项目定义主键
     * @return 规则列表
     */
    List<ChargeItemAutoGen> findAllByChargeDefinitionIdAndIsDeletedFalseOrderByIdAsc(Long chargeDefinitionId);

    /**
     * 分页列出全部规则（管理端；须配合 Specification 软删过滤）。
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ChargeItemAutoGen> findAllBy(Pageable pageable);
}
