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
