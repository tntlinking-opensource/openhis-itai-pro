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
 * InventoryItemCreatedEvent 领域事件。
 */
@Getter
@Setter
public class InventoryItemCreatedEvent extends DomainEvent {

    /** 物品定义标识。 */
    private Long itemId;

    /** 产品批号。 */
    private String lotNumber;

    /** 库房标识。 */
    private Long locationId;

    /** 初始数量。 */
    private java.math.BigDecimal quantity;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InventoryItemCreatedEvent() {
        super();
    }

    public InventoryItemCreatedEvent(
        Long aggregateId,
        Long itemId,
        String lotNumber,
        Long locationId,
        java.math.BigDecimal quantity,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.INVENTORY_ITEM);
        this.itemId = itemId;
        this.lotNumber = lotNumber;
        this.locationId = locationId;
        this.quantity = quantity;
        this.operatorId = operatorId;
    }
}
