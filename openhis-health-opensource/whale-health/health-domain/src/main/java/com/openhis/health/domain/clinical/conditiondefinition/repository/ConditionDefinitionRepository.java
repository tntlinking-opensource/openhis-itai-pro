/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.repository;

import com.openhis.health.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 诊断定义仓储。
 */
public interface ConditionDefinitionRepository extends JpaRepository<ConditionDefinition, Long>, JpaSpecificationExecutor<ConditionDefinition> {
    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<ConditionDefinition> findById(Long id);

    /**
     * 判断是否存在满足条件的记录。
     * @param conditionCode 条件
     * @return 查询结果
     */
    boolean existsByConditionCode(String conditionCode);

    /**
     * 判断是否存在满足条件的记录。
     * @param conditionCode 条件
     * @param id 条件
     * @return 查询结果
     */
    boolean existsByConditionCodeAndIdNot(String conditionCode, Long id);

    /**
     * 按条件查询。
     * @param ids 条件
     * @return 查询结果
     */
    List<ConditionDefinition> findByIdIn(List<Long> ids);

    /**
     * 按条件查询。
     * @param name 条件
     * @param conditionCode 条件
     * @param pageable 条件
     * @return 查询结果
     */
    Page<ConditionDefinition> findByNameContainingOrConditionCodeContaining(String name, String conditionCode, Pageable pageable);
}
