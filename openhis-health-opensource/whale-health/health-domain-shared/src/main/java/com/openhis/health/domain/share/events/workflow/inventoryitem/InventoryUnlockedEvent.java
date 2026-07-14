/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
