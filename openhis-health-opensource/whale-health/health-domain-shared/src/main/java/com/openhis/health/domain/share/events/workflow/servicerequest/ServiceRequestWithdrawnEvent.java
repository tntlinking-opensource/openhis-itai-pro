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
 * 服务申请已签退事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）、signCode。</p>
 */
@Getter
@Setter
public class ServiceRequestWithdrawnEvent extends DomainEvent {

    /** 原签发码（用于下游撤销签发关联记录）。 */
    private String signCode;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestWithdrawnEvent() {
        super();
    }

    /**
     * 构造服务申请已签退事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param signCode         原签发码
     */
    public ServiceRequestWithdrawnEvent(Long serviceRequestId, String signCode) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.signCode = signCode;
    }
}
