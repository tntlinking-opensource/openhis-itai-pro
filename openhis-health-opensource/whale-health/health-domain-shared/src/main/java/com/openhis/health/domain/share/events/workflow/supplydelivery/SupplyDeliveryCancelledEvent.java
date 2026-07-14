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
 * 供应发放已取消事件（DM-WKF-005 §5.1 事件 #3）。
 *
 * <p>触发时机：汇总路径取消/拒绝后。</p>
 */
@Getter
@Setter
public class SupplyDeliveryCancelledEvent extends DomainEvent {

    /** 供应请求标识。 */
    private Long requestId;

    /** 变更原因码（可选）。 */
    private String changeReasonCode;

    /**
     * 无参构造（反序列化用）。
     */
    public SupplyDeliveryCancelledEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId      供应发放标识
     * @param requestId        供应请求标识
     * @param changeReasonCode 变更原因码（可选）
     */
    public SupplyDeliveryCancelledEvent(Long aggregateId, Long requestId, String changeReasonCode) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_DELIVERY);
        this.requestId = requestId;
        this.changeReasonCode = changeReasonCode;
    }
}
