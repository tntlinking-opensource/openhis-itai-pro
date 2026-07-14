/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.supplydelivery;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应发放已删除事件（DM-WKF-005 §5.1 事件 #4）。
 *
 * <p>触发时机：{@code deleteSupplyDeliveryByReqId} 逻辑删除后。</p>
 */
@Getter
@Setter
public class SupplyDeliveryDeletedEvent extends DomainEvent {

    /** 供应请求标识。 */
    private Long requestId;

    /**
     * 无参构造（反序列化用）。
     */
    public SupplyDeliveryDeletedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId 供应发放标识
     * @param requestId   供应请求标识
     */
    public SupplyDeliveryDeletedEvent(Long aggregateId, Long requestId) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_DELIVERY);
        this.requestId = requestId;
    }
}
