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
 * 已执行服务申请已创建事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）、basedOnTable、basedOnId。</p>
 */
@Getter
@Setter
public class ServiceRequestExecutedCreatedEvent extends DomainEvent {

    /** 来源表名（如 wkf_device_request、wkf_medication_request 等）。 */
    private String basedOnTable;

    /** 来源记录主键。 */
    private Long basedOnId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestExecutedCreatedEvent() {
        super();
    }

    /**
     * 构造已执行服务申请已创建事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param basedOnTable     来源表名
     * @param basedOnId        来源记录主键
     */
    public ServiceRequestExecutedCreatedEvent(Long serviceRequestId, String basedOnTable, Long basedOnId) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.basedOnTable = basedOnTable;
        this.basedOnId = basedOnId;
    }
}
