/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.inventoryitem;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * InventoryQuantityIncreasedEvent 领域事件。
 */
@Getter
@Setter
public class InventoryQuantityIncreasedEvent extends DomainEvent {

    /** 增加量。 */
    private java.math.BigDecimal increaseQty;

    /** 增加后数量。 */
    private java.math.BigDecimal afterQty;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InventoryQuantityIncreasedEvent() {
        super();
    }

    public InventoryQuantityIncreasedEvent(
        Long aggregateId,
        java.math.BigDecimal increaseQty,
        java.math.BigDecimal afterQty,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.INVENTORY_ITEM);
        this.increaseQty = increaseQty;
        this.afterQty = afterQty;
        this.operatorId = operatorId;
    }
}
