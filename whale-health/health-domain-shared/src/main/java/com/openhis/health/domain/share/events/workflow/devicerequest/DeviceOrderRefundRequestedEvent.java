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

package com.openhis.health.domain.share.events.workflow.devicerequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 耗材退货已申请事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、refundDeviceId（原耗材请求主键）。</p>
 */
@Getter
@Setter
public class DeviceOrderRefundRequestedEvent extends DomainEvent {

    /** 原耗材请求主键（退货冲销来源）。 */
    private Long refundDeviceId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public DeviceOrderRefundRequestedEvent() {
        super();
    }

    /**
     * 构造耗材退货已申请事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param refundDeviceId  原耗材请求主键
     */
    public DeviceOrderRefundRequestedEvent(Long deviceRequestId, Long refundDeviceId) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.refundDeviceId = refundDeviceId;
    }
}
