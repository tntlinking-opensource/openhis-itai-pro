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
