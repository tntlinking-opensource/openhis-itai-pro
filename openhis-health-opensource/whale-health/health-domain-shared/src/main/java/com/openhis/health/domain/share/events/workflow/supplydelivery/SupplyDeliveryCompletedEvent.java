/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
