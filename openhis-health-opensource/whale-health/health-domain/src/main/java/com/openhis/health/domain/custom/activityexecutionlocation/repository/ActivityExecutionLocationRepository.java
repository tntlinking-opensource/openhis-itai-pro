/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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

