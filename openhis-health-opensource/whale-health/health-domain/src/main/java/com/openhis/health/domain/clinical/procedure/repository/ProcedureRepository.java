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

package com.openhis.health.domain.clinical.procedure.repository;

import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 执行记录仓储。
 */
public interface ProcedureRepository extends JpaRepository<Procedure, Long>, JpaSpecificationExecutor<Procedure> {
    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<Procedure> findById(Long id);

    /**
     * 按条件查询。
     * @param requestId 条件
     * @param requestTable 条件
     * @return 查询结果
     */
    List<Procedure> findByRequestIdAndRequestTable(Long requestId, String requestTable);

    /**
     * 按就诊主键查询该就诊下全部执行记录（护士站净剩列表等场景）。
     *
     * @param encounterId 就诊主键
     * @return 执行记录列表；无命中时返回空集合
     */
    List<Procedure> findByEncounterId(Long encounterId);

    /**
     * 按就诊与来源请求缩窄查询执行记录。
     *
     * @param encounterId  就诊主键
     * @param requestId    来源请求主键
     * @param requestTable 来源请求表持久化名
     * @return 执行记录列表；无命中时返回空集合
     */
    List<Procedure> findByEncounterIdAndRequestIdAndRequestTable(Long encounterId, Long requestId, String requestTable);

    /**
     * 按就诊主键集合批量查询执行记录（护士站当日净执行统计）。
     *
     * @param encounterIds 就诊主键集合
     * @return 执行记录列表
     */
    List<Procedure> findByEncounterIdIn(Collection<Long> encounterIds);

    /**
     * 按主键集合批量查询执行记录（DM-CLN-004 §6.2.8；AS-IP-NUR-006）。
     *
     * @param ids 主键集合
     * @return 执行记录列表
     */
    List<Procedure> findAllByIdIn(Collection<Long> ids);

    /**
     * 是否存在仍占用计划点的终态执行记录（GAP-EXE-002 域层门禁）。
     */
    boolean existsByRequestIdAndRequestTableAndOccurrenceTimeAndOccurrenceSlotActiveTrueAndStatusEnumInAndIsDeletedFalse(
        Long requestId,
        String requestTable,
        LocalDateTime occurrenceTime,
        Collection<String> statusEnums);
}
