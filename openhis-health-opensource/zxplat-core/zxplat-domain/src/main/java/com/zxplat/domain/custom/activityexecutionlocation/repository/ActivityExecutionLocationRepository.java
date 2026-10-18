/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.activityexecutionlocation.repository;

import com.zxplat.domain.custom.activityexecutionlocation.entity.ActivityExecutionLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 执行科室规则仓储（仅本聚合表 {@code cus_activity_execution_location}）。
 *
 * <p>重叠校验与配置读侧均以开立科室 {@code organizationId} 为锚点。</p>
 */
public interface ActivityExecutionLocationRepository extends JpaRepository<ActivityExecutionLocation, Long> {

    /**
     * 按主键查询未删除规则。
     *
     * @param id 主键
     * @return 规则
     */
    Optional<ActivityExecutionLocation> findById(Long id);

    /**
     * 按开立科室列出全部有效规则（配置页左树选中后拉全量）。
     *
     * @param organizationId 开立科室标识
     * @return 规则列表
     */
    List<ActivityExecutionLocation> findAllByOrganizationId(Long organizationId);

    /**
     * 按开立科室 + 活动类别列出规则。
     *
     * @param organizationId 开立科室标识
     * @param activityCategoryCode 活动类别编码
     * @return 规则列表
     */
    List<ActivityExecutionLocation> findAllByOrganizationIdAndActivityCategoryCode(
        Long organizationId,
        String activityCategoryCode
    );

    /**
     * 按开立科室 + 活动类别 + 诊疗定义查询候选规则（项目级重叠校验）。
     *
     * @param organizationId 开立科室标识
     * @param activityCategoryCode 活动类别编码
     * @param activityDefinitionId 诊疗定义标识
     * @return 候选规则
     */
    List<ActivityExecutionLocation> findAllByOrganizationIdAndActivityCategoryCodeAndActivityDefinitionId(
        Long organizationId,
        String activityCategoryCode,
        Long activityDefinitionId
    );

    /**
     * 批量按开立科室 + 活动类别 + 诊疗定义集合查询候选规则。
     *
     * @param organizationId 开立科室标识
     * @param activityCategoryCode 活动类别编码
     * @param activityDefinitionIds 诊疗定义标识集合
     * @return 候选规则
     */
    List<ActivityExecutionLocation> findAllByOrganizationIdAndActivityCategoryCodeAndActivityDefinitionIdIn(
        Long organizationId,
        String activityCategoryCode,
        Collection<Long> activityDefinitionIds
    );

    /**
     * 按开立科室 + 活动类别查询大类默认规则（未绑定具体诊疗项目）。
     *
     * @param organizationId 开立科室标识
     * @param activityCategoryCode 活动类别编码
     * @return 大类级候选规则
     */
    List<ActivityExecutionLocation> findAllByOrganizationIdAndActivityCategoryCodeAndActivityDefinitionIdIsNull(
        Long organizationId,
        String activityCategoryCode
    );

    /**
     * 按开立科室 + 诊疗定义查询项目级规则（医生站解析）。
     *
     * @param organizationId 开立科室标识
     * @param activityDefinitionId 诊疗定义标识
     * @return 规则列表
     */
    List<ActivityExecutionLocation> findAllByOrganizationIdAndActivityDefinitionId(
        Long organizationId,
        Long activityDefinitionId
    );

    /**
     * 批量按开立科室 + 诊疗定义集合查询项目级规则。
     *
     * @param organizationId 开立科室标识
     * @param activityDefinitionIds 诊疗定义标识集合
     * @return 规则列表
     */
    List<ActivityExecutionLocation> findAllByOrganizationIdAndActivityDefinitionIdIn(
        Long organizationId,
        Collection<Long> activityDefinitionIds
    );
}
