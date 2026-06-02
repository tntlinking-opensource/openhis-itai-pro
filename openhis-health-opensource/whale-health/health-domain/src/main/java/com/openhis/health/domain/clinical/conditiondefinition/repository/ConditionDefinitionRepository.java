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
