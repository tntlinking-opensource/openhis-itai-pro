/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.repository;

import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 库存项仓储。
 *
 * <p>约束：禁止 {@code @Query}；过滤条件必须下推数据库侧。</p>
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long>, JpaSpecificationExecutor<InventoryItem> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<InventoryItem> findById(Long id);

    /**
     * 按条件查询。
     * @param idList 条件
     * @return 查询结果
     */
    List<InventoryItem> findByIdIn(List<Long> idList);

    Optional<InventoryItem> findFirstBySupplierIdAndLotNumberAndLocationIdAndItemTableAndItemId(
        Long supplierId, String lotNumber, Long locationId, String itemTable, Long itemId);

    /**
     * 按批号、库房、物品定位候选库存行（同一物品同一批次在同一库房理论上唯一；多行时按 id 升序取首条前由调用方处理歧义）。
     *
     * @param lotNumber   批号
     * @param locationId  库房库位
     * @param itemTable   物品来源表名
     * @param itemId      物品定义主键
     * @return 匹配行列表（可能为空）
     */
    List<InventoryItem> findByLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc(
        String lotNumber, Long locationId, String itemTable, Long itemId);

    /**
     * 按主键集合加悲观写锁（护士站/签发 FIFO Gate GAP-EXE-001/005）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<InventoryItem> findWithLockByIdIn(Collection<Long> ids);
}
