/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.document.inventoryitemsnapshot.repository;

import com.openhis.health.domain.document.inventoryitemsnapshot.entity.DocInventoryItemSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 库存明细快照仓储（DM-DOC-001）。
 *
 * <p>append-only：仅 INSERT；禁止 {@code @Query}。</p>
 * <p>同批次重复源行由 Flyway UK {@code (tenant_id, bus_no, inventory_item_id)} 兜底（BR-DOC-SNAP-002）。</p>
 */
public interface DocInventoryItemSnapshotRepository
    extends JpaRepository<DocInventoryItemSnapshotEntity, Long>, JpaSpecificationExecutor<DocInventoryItemSnapshotEntity> {

    /**
     * 判定租户在自然日是否已有 ABU 前缀的成功快照批次（BR-DOC-SNAP-007 / AS BR-INV-DTL-020）。
     *
     * @param tenantId     租户
     * @param busNoPrefix  {@code ABU}+{@code yyyyMMdd} 前缀
     * @return 存在未删行时为 true
     */
    boolean existsByTenantIdAndBusNoStartingWithAndIsDeletedFalse(Long tenantId, String busNoPrefix);
}
