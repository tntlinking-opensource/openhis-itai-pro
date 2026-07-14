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
 * InventoryBelowThresholdEvent 领域事件。
 */
@Getter
@Setter
public class InventoryBelowThresholdEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public InventoryBelowThresholdEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, Long operatorId逻辑。
     * @return 返回结果
     */
    public InventoryBelowThresholdEvent(Long aggregateId, String busNo, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.INVENTORY_ITEM);
        this.busNo = busNo;
        this.operatorId = operatorId;
    }
}
