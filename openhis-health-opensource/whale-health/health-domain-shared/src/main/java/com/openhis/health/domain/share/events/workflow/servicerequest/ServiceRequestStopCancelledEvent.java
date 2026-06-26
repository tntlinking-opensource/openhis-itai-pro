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

/**
 * 诊疗服务申请已取消停嘱领域事件（IF-IP-DOR-25）。
 */
@Getter
@Setter
public class ServiceRequestStopCancelledEvent extends DomainEvent {

    /** 取消停嘱后请求状态（{@link com.openhis.health.domain.share.enums.RequestStatus#getCode()}）。 */
    private String statusEnum;

    /** 无参构造，供序列化框架使用。 */
    public ServiceRequestStopCancelledEvent() {
        super();
    }

    /**
     * 构造取消停嘱事件载荷。
     *
     * @param serviceRequestId 服务申请主键
     * @param statusEnum       恢复后的状态 code
     */
    public ServiceRequestStopCancelledEvent(Long serviceRequestId, String statusEnum) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.statusEnum = statusEnum;
    }
}
