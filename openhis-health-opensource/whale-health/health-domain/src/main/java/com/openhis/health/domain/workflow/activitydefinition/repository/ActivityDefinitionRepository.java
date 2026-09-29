/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.activitydefinition.repository;

import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 诊疗项目定义仓储。
 *
 * <p>约束：禁止使用 {@code @Query}，所有过滤条件（含软删）必须在方法签名中下推到数据库侧完成。</p>
 */
public interface ActivityDefinitionRepository extends JpaRepository<ActivityDefinition, Long>, JpaSpecificationExecutor<ActivityDefinition> {

    /**
     * 按主键加载未删除记录。
     *
     * @param id 主键
     * @return 聚合根
     */
    Optional<ActivityDefinition> findById(Long id);

    /**
     * 批量加载未删除记录。
     *
     * @param ids 主键集合
     * @return 聚合根集合
     */
    List<ActivityDefinition> findByIdIn(List<Long> ids);

    /**
     * 业务编码唯一性校验（默认在未删除记录范围内约束）。
     *
     * @param busNo 业务编码
     * @return 是否已存在
     */
    boolean existsByBusNo(String busNo);

    /**
     * 批量查询业务编码对应的未删除记录，用于批量导入与批量唯一性校验。
     *
     * @param busNos 业务编码集合
     * @return 已存在记录集合
     */
    List<ActivityDefinition> findByBusNoIn(List<String> busNos);

    /**
     * 按业务编码加载未删除记录。
     *
     * @param busNo 业务编码
     * @return 聚合根
     */
    Optional<ActivityDefinition> findByBusNo(String busNo);

    /**
     * 未软删范围内是否已存在该精确名称（产品内置名唯一性门禁）。
     *
     * @param name 项目名称
     * @return 已存在则为 true
     */
    boolean existsByNameAndIsDeletedFalse(String name);

    /**
     * 查询所有未删除记录（导出等场景）。
     *
     * <p>注意：返回集合可能较大，调用方需评估规模风险。</p>
     *
     * @return 未删除记录集合
     */
    List<ActivityDefinition> findAllBy();
}
