/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
