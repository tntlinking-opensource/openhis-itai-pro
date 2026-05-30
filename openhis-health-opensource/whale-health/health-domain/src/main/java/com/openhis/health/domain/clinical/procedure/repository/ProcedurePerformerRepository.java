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

import com.openhis.health.domain.clinical.procedure.entity.ProcedurePerformer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 执行人仓储。
 */
public interface ProcedurePerformerRepository extends JpaRepository<ProcedurePerformer, Long> {
    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<ProcedurePerformer> findById(Long id);

    /**
     * 按条件查询。
     * @param id 条件
     * @param procedureId 条件
     * @return 查询结果
     */
    Optional<ProcedurePerformer> findByIdAndProcedureId(Long id, Long procedureId);

    /**
     * 按条件查询列表。
     * @param procedureId 条件
     * @return 查询结果
     */
    List<ProcedurePerformer> findAllByProcedureId(Long procedureId);

    /**
     * 按执行记录主键集合批量查询执行人（读侧批量装配，避免 N+1）。
     *
     * @param procedureIds 执行记录主键集合
     * @return 执行人行列表
     */
    List<ProcedurePerformer> findAllByProcedureIdIn(Collection<Long> procedureIds);
}
