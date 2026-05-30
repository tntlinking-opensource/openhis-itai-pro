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

package com.openhis.health.domain.custom.chargeitemautogen.repository;

import com.openhis.health.domain.custom.chargeitemautogen.entity.ChargeItemAutoGen;
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

    Optional<ChargeItemAutoGen> findById(Long id);

    /**
     * BR-软删唯一性语义分析表（生成后补齐，避免“已删除记录占用唯一键”或“误放过冲突”）。
     *
     * <p>说明：当前仓储方法用于“同一触发键下组合唯一”校验。由于领域文档未在此文件内内联展示，
     * 这里按“有效记录内唯一”为默认口径：即仅对 {@code isDeleted=false} 的记录做冲突判定；
     * 若文档实际要求“含已逻辑删除记录仍占用唯一键”，则需将下列方法签名去掉 {@code } 并标记 DOC-CONFLICT。</p>
     *
     * <pre>
     * | BR 编号 | 原文关键词 | 含已逻辑删除记录？ | 结论 Repository 方法签名 | DOC-CONFLICT 留痕 |
     * | BR-CAG-001 | 组合唯一/触发键 | 模糊（默认按有效记录） | existsByTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndTargetDefinitionId | DOC-CONFLICT: 唯一性是否含已删待文档确认 |
     * </pre>
     *
     * @param triggerSourceTable 触发来源表名
     * @param triggerSourceId 触发来源主键
     * @param performingOrgId 执行机构标识
     * @param targetDefinitionId 目标收费项定义主键
     * @return true 表示冲突存在
     */
    boolean existsByTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndTargetDefinitionId(
        String triggerSourceTable,
        Long triggerSourceId,
        Long performingOrgId,
        Long targetDefinitionId
    );

    boolean existsByTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndTargetDefinitionIdAndIdNot(
        String triggerSourceTable,
        Long triggerSourceId,
        Long performingOrgId,
        Long targetDefinitionId,
        Long id
    );

    List<ChargeItemAutoGen> findAllByTriggerSourceTableAndTriggerSourceIdOrderByPriorityAscIdAsc(
        String triggerSourceTable,
        Long triggerSourceId
    );

    List<ChargeItemAutoGen> findAllByTargetDefinitionIdOrderByIdAsc(Long targetDefinitionId);

    Page<ChargeItemAutoGen> findAllBy(Pageable pageable);
}

