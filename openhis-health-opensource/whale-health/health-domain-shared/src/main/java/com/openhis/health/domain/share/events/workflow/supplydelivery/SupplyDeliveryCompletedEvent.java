/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.supplydelivery;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 供应发放已完成事件（DM-WKF-005 §5.1 事件 #2）。
 *
 * <p>触发时机：{@code updateCompletedStatusBatch} 或汇总路径最终确认后。</p>
 */
@Getter
@Setter
public class SupplyDeliveryCompletedEvent extends DomainEvent {

    /** 供应请求标识。 */
    private Long requestId;

    /** 实际发生时间。 */
    private LocalDateTime occurrenceTime;

    /**
     * 无参构造（反序列化用）。
     */
    public SupplyDeliveryCompletedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId    供应发放标识
     * @param requestId      供应请求标识
     * @param occurrenceTime 实际发生时间
     */
    public SupplyDeliveryCompletedEvent(Long aggregateId, Long requestId, LocalDateTime occurrenceTime) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_DELIVERY);
        this.requestId = requestId;
        this.occurrenceTime = occurrenceTime;
    }
}
