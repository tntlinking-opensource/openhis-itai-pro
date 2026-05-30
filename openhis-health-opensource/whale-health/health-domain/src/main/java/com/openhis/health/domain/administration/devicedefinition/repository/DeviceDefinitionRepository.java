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

package com.openhis.health.domain.administration.devicedefinition.repository;

import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceDefinitionRepository extends JpaRepository<DeviceDefinition, Long>, JpaSpecificationExecutor<DeviceDefinition> {
    Optional<DeviceDefinition> findById(Long id);
    List<DeviceDefinition> findAllByIdIn(Collection<Long> ids);
    /**
     * 校验业务编码是否存在（BR-DEVDEF-001）。
     *
     * <p>DOC-CONFLICT：DM/SDD 未明确唯一性是否“包含已逻辑删除记录”；当前按“全局唯一”处理（含已逻辑删除记录）。</p>
     *
     * @param busNo 业务编码（已归一化 trim）
     * @return true=存在
     */
    boolean existsByBusNo(String busNo);
    /**
     * 批量按业务编码查询器材定义，用于导入冲突判定（DM-ADM-008 §6.1.5，SDD §5.1.5）。
     *
     * <p>说明：业务编码在聚合内被视为全局唯一键；批量导入必须一次性下推数据库侧查询，禁止循环单条查询。</p>
     *
     * @param busNos 业务编码集合（已归一化 trim）
     * @return 已存在的器材定义集合
     */
    List<DeviceDefinition> findByBusNoIn(Collection<String> busNos);
    List<DeviceDefinition> findAllByStatusEnum(String statusEnum);
    Page<DeviceDefinition> findAllBy(Pageable pageable);
}

