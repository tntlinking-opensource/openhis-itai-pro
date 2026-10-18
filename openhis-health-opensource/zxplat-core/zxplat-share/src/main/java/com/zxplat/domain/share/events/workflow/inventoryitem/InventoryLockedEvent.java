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
 * InventoryLockedEvent 领域事件。
 */
@Getter
@Setter
public class InventoryLockedEvent extends DomainEvent {

    /** 库存项标识。 */
    private Long inventoryId;

    /** 业务单号。 */
    private String busNo;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InventoryLockedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long inventoryId, String busNo, Long operatorId逻辑。
     * @return 返回结果
     */
    public InventoryLockedEvent(Long aggregateId, Long inventoryId, String busNo, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.INVENTORY_ITEM);
        this.inventoryId = inventoryId;
        this.busNo = busNo;
        this.operatorId = operatorId;
    }
}
