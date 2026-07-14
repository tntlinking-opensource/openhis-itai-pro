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
 * InventorySupplyStoppedEvent 领域事件。
 */
@Getter
@Setter
public class InventorySupplyStoppedEvent extends DomainEvent {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InventorySupplyStoppedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long operatorId逻辑。
     * @return 返回结果
     */
    public InventorySupplyStoppedEvent(Long aggregateId, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.INVENTORY_ITEM);
        this.operatorId = operatorId;
    }
}
