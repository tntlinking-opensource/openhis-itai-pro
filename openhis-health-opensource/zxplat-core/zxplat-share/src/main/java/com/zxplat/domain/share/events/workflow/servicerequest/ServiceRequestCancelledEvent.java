/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.servicerequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务申请已取消事件。
 *
 * <p>SDD-WKF-004 §7 最小载荷：serviceRequestId（via super.aggregateId）、cancelReason。</p>
 */
@Getter
@Setter
public class ServiceRequestCancelledEvent extends DomainEvent {

    /** 取消原因。 */
    private String cancelReason;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceRequestCancelledEvent() {
        super();
    }

    /**
     * 构造服务申请已取消事件。
     *
     * @param serviceRequestId 服务申请主键（aggregateId）
     * @param cancelReason     取消原因
     */
    public ServiceRequestCancelledEvent(Long serviceRequestId, String cancelReason) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.cancelReason = cancelReason;
    }
}
