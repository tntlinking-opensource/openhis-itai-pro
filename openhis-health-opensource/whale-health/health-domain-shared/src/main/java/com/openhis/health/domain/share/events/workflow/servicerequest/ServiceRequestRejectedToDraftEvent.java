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
 * 服务申请已退回草稿事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）。</p>
 */
@Getter
@Setter
public class ServiceRequestRejectedToDraftEvent extends DomainEvent {

    /** 持久化状态码（RequestStatus#getCode()，save 后取自写模型）。 */
    private String statusEnum;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestRejectedToDraftEvent() {
        super();
    }

    /**
     * 构造服务申请已退回草稿事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param statusEnum       退回后状态码
     */
    public ServiceRequestRejectedToDraftEvent(Long serviceRequestId, String statusEnum) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.statusEnum = statusEnum;
    }
}
