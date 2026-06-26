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
     * 同一就诊 + 触发键 + 执行科室 + 定价组合是否已存在（BR-CAG-001）。
     */
    boolean existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionId(
        Long encounterId,
        String triggerSourceTable,
        Long triggerSourceId,
        Long performingOrgId,
        Long chargeDefinitionId
    );

    boolean existsByEncounterIdAndTriggerSourceTableAndTriggerSourceIdAndPerformingOrgIdAndChargeDefinitionIdAndIdNot(
        Long encounterId,
        String triggerSourceTable,
        Long triggerSourceId,
        Long performingOrgId,
        Long chargeDefinitionId,
        Long id
    );

    List<ChargeItemAutoGen> findAllByTriggerSourceTableAndTriggerSourceIdAndIsDeletedFalseOrderByPriorityAscIdAsc(
        String triggerSourceTable,
        Long triggerSourceId
    );

    List<ChargeItemAutoGen> findAllByEncounterIdAndIsDeletedFalseOrderByPriorityAscIdAsc(Long encounterId);

    List<ChargeItemAutoGen> findAllByChargeDefinitionIdAndIsDeletedFalseOrderByIdAsc(Long chargeDefinitionId);

    Page<ChargeItemAutoGen> findAllBy(Pageable pageable);
}
