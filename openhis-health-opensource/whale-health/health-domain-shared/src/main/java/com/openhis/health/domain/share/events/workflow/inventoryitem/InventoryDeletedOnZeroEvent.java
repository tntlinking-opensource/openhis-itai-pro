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
 * InventoryDeletedOnZeroEvent 领域事件。
 */
@Getter
@Setter
public class InventoryDeletedOnZeroEvent extends DomainEvent {

    /** 物品定义标识。 */
    private Long itemId;

    /** 产品批号。 */
    private String lotNumber;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InventoryDeletedOnZeroEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long itemId, String lotNumber, Long operatorId逻辑。
     * @return 返回结果
     */
    public InventoryDeletedOnZeroEvent(Long aggregateId, Long itemId, String lotNumber, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.INVENTORY_ITEM);
        this.itemId = itemId;
        this.lotNumber = lotNumber;
        this.operatorId = operatorId;
    }
}
