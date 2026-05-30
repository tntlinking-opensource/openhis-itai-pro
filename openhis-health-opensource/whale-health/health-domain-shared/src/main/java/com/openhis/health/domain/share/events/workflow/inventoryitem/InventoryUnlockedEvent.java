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
 * InventoryUnlockedEvent 领域事件。
 */
@Getter
@Setter
public class InventoryUnlockedEvent extends DomainEvent {

    /** 库存项标识。 */
    private Long inventoryId;

    /** 业务单号。 */
    private String busNo;

    /** 解锁类型（1 正常解锁；2 强制解锁）。 */
    private Integer unlockType;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InventoryUnlockedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long inventoryId, String busNo, Integer unlockType, Long operatorId逻辑。
     * @return 返回结果
     */
    public InventoryUnlockedEvent(Long aggregateId, Long inventoryId, String busNo, Integer unlockType, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.INVENTORY_ITEM);
        this.inventoryId = inventoryId;
        this.busNo = busNo;
        this.unlockType = unlockType;
        this.operatorId = operatorId;
    }
}
