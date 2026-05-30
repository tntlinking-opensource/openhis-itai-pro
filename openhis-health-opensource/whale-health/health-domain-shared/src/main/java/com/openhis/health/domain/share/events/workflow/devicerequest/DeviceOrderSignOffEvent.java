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
 * 耗材请求已签退事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、status（签退后状态枚举）。</p>
 */
@Getter
@Setter
public class DeviceOrderSignOffEvent extends DomainEvent {

    /** 签退后状态枚举。 */
    private String status;

    /**
     * 默认构造函数（反序列化用）。
     */
    public DeviceOrderSignOffEvent() {
        super();
    }

    /**
     * 构造耗材请求已签退事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param status          签退后状态枚举
     */
    public DeviceOrderSignOffEvent(Long deviceRequestId, String status) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.status = status;
    }
}
