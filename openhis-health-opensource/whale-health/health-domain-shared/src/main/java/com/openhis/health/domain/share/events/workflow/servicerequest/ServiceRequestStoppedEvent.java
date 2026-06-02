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

package com.openhis.health.domain.share.events.workflow.servicerequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 服务申请已停嘱事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）、stopTime。</p>
 */
@Getter
@Setter
public class ServiceRequestStoppedEvent extends DomainEvent {

    /** 停嘱时间。 */
    private LocalDateTime stopTime;

    /** 停嘱后请求状态（{@link com.openhis.health.domain.share.enums.RequestStatus#getCode()}，如 STOPPED）。 */
    private String statusEnum;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestStoppedEvent() {
        super();
    }

    /**
     * 构造服务申请已停嘱事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param stopTime         停嘱时间
     */
    public ServiceRequestStoppedEvent(Long serviceRequestId, LocalDateTime stopTime, String statusEnum) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.stopTime = stopTime;
        this.statusEnum = statusEnum;
    }
}
