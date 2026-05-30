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

package com.openhis.health.domain.share.events.workflow.inventoryitem;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * InventoryAllocatedEvent 领域事件。
 */
@Getter
@Setter
public class InventoryAllocatedEvent extends DomainEvent {

    /** 分配明细列表（批次分配结果）。 */
    private java.util.List<AllocationResultItem> allocationResults;

    /** 总分配量。 */
    private java.math.BigDecimal totalAllocated;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InventoryAllocatedEvent() {
        super();
    }

    public InventoryAllocatedEvent(
        Long aggregateId,
        java.util.List<AllocationResultItem> allocationResults,
        java.math.BigDecimal totalAllocated,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.INVENTORY_ITEM);
        this.allocationResults = allocationResults;
        this.totalAllocated = totalAllocated;
        this.operatorId = operatorId;
    }

    /**
     * 分配明细项。
     */
    @Getter
    @Setter
    public static class AllocationResultItem {

        /** 库存项标识。 */
        private Long inventoryItemId;

        /** 产品批号。 */
        private String lotNumber;

        /** 本批次分配数量。 */
        private java.math.BigDecimal allocatedQuantity;

        /** 本批次分配后剩余数量。 */
        private java.math.BigDecimal remainingQuantity;

        /** 库房标识。 */
        private Long locationId;

        /** 货位标识。 */
        private Long locationStoreId;

        /** 效期。 */
        private java.time.LocalDateTime expirationDate;
    }
}
