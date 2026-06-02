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

package com.openhis.health.domain.workflow.inventoryitem.repository;

import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 库存锁仓储。
 *
 * <p>约束：禁止 {@code @Query}；过滤条件必须下推数据库侧。</p>
 */
public interface InventoryLockRepository extends JpaRepository<InventoryLock, Long> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<InventoryLock> findById(Long id);

    /**
     * 按条件查询。
     * @param inventoryId 条件
     * @return 查询结果
     */
    Optional<InventoryLock> findByInventoryIdAndLockFlagTrue(Long inventoryId);

    /**
     * 按条件查询。
     * @param inventoryIdList 条件
     * @return 查询结果
     */
    List<InventoryLock> findByInventoryIdInAndLockFlagTrue(List<Long> inventoryIdList);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @return 查询结果
     */
    List<InventoryLock> findByBusNoAndLockFlagTrueOrderByOrderFlagAsc(String busNo);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @return 查询结果
     */
    List<InventoryLock> findByBusNoOrderByOrderFlagAsc(String busNo);
}
