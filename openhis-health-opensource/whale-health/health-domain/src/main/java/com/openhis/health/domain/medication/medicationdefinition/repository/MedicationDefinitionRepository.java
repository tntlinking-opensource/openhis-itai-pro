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

package com.openhis.health.domain.medication.medicationdefinition.repository;

import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 药品定义仓储。
 */
public interface MedicationDefinitionRepository extends JpaRepository<MedicationDefinition, Long>,
    JpaSpecificationExecutor<MedicationDefinition> {

    /**
     * 查询未删除记录。
     *
     * @param id 主键
     * @return 药品定义
     */
    Optional<MedicationDefinition> findById(Long id);

    /**
     * 校验机构内编码唯一。
     *
     * @param orgId 机构
     * @param busNo 业务编码
     * @return 是否存在
     */
    boolean existsByOrgIdAndBusNo(Long orgId, String busNo);

    /**
     * 批量查询机构+编码组合，用于批量唯一性校验。
     *
     * @param orgIds 机构集合
     * @param busNos 编码集合
     * @return 命中记录
     */
    List<MedicationDefinition> findByOrgIdInAndBusNoIn(List<Long> orgIds, List<String> busNos);

    /**
     * 查询未删除药品定义集合。
     *
     * @return 未删除记录
     */
    List<MedicationDefinition> findAllBy();
}

