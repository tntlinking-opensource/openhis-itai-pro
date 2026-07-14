/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.servicerequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务申请已签退事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）。</p>
 */
@Getter
@Setter
public class ServiceRequestWithdrawnEvent extends DomainEvent {

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
     */
    public ServiceRequestWithdrawnEvent(Long serviceRequestId) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
    }
}
