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
 * InventoryQuantityDecreasedEvent 领域事件。
 */
@Getter
@Setter
public class InventoryQuantityDecreasedEvent extends DomainEvent {

    /** 减少量。 */
    private java.math.BigDecimal decreaseQty;

    /** 减少后数量。 */
    private java.math.BigDecimal afterQty;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InventoryQuantityDecreasedEvent() {
        super();
    }

    public InventoryQuantityDecreasedEvent(
        Long aggregateId,
        java.math.BigDecimal decreaseQty,
        java.math.BigDecimal afterQty,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.INVENTORY_ITEM);
        this.decreaseQty = decreaseQty;
        this.afterQty = afterQty;
        this.operatorId = operatorId;
    }
}
