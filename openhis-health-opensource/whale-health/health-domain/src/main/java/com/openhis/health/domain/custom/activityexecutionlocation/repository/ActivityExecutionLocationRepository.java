/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityexecutionlocation.repository;

import com.openhis.health.domain.custom.activityexecutionlocation.entity.ActivityExecutionLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 执行科室规则仓储。
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
     * 按锚点查询候选规则（诊疗定义维度）。
     *
     * @param executionOrgId 执行科室标识
     * @param activityCategoryCode 活动类别编码
     * @param activityDefinitionId 诊疗定义标识
     * @return 候选规则
     */
    List<ActivityExecutionLocation> findAllByExecutionOrgIdAndActivityCategoryCodeAndActivityDefinitionId(
        Long executionOrgId, String activityCategoryCode, Long activityDefinitionId);

    /**
     * 批量按锚点查询候选规则（诊疗定义维度）。
     *
     * @param executionOrgId 执行科室标识
     * @param activityCategoryCode 活动类别编码
     * @param activityDefinitionIds 诊疗定义标识集合
     * @return 候选规则
     */
    List<ActivityExecutionLocation> findAllByExecutionOrgIdAndActivityCategoryCodeAndActivityDefinitionIdIn(
        Long executionOrgId,
        String activityCategoryCode,
        Collection<Long> activityDefinitionIds
    );

    /**
     * 按诊疗定义查询规则列表。
     *
     * @param activityDefinitionId 诊疗定义标识
     * @return 规则列表
     */
    List<ActivityExecutionLocation> findAllByActivityDefinitionId(Long activityDefinitionId);

    /**
     * 批量按诊疗定义查询规则列表。
     *
     * @param activityDefinitionIds 诊疗定义标识集合
     * @return 规则列表
     */
    List<ActivityExecutionLocation> findAllByActivityDefinitionIdIn(Collection<Long> activityDefinitionIds);

    /**
     * 按执行科室查询规则列表。
     *
     * @param executionOrgId 执行科室标识
     * @return 规则列表
     */
    List<ActivityExecutionLocation> findAllByExecutionOrgId(Long executionOrgId);

    /**
     * 按执行科室 + 活动类别查询规则列表。
     *
     * @param executionOrgId 执行科室标识
     * @param activityCategoryCode 活动类别编码
     * @return 规则列表
     */
    List<ActivityExecutionLocation> findAllByExecutionOrgIdAndActivityCategoryCode(
        Long executionOrgId,
        String activityCategoryCode
    );

    /**
     * 按执行科室 + 活动类别查询大类默认规则（未绑定具体诊疗项目）。
     *
     * @param executionOrgId 执行科室标识
     * @param activityCategoryCode 活动类别编码
     * @return 大类级候选规则
     */
    List<ActivityExecutionLocation> findAllByExecutionOrgIdAndActivityCategoryCodeAndActivityDefinitionIdIsNull(
        Long executionOrgId,
        String activityCategoryCode
    );

    /**
     * 按活动类别查询全院大类默认规则（未绑定具体诊疗项目）。
     *
     * @param activityCategoryCode 活动类别编码
     * @return 大类级规则列表
     */
    List<ActivityExecutionLocation> findAllByActivityCategoryCodeAndActivityDefinitionIdIsNull(
        String activityCategoryCode
    );
}
