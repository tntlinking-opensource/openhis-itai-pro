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
 * 诊疗退费负向申请已生成事件。
 *
 * <p>最小载荷：serviceRequestId（负向单）、refundServiceId（原单）。</p>
 */
@Getter
@Setter
public class ServiceOrderRefundRequestedEvent extends DomainEvent {

    /** 原服务申请主键（退费冲销来源）。 */
    private Long refundServiceId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ServiceOrderRefundRequestedEvent() {
        super();
    }

    /**
     * 构造诊疗退费负向申请已生成事件。
     *
     * @param serviceRequestId 负向服务申请主键
     * @param refundServiceId  原服务申请主键
     */
    public ServiceOrderRefundRequestedEvent(Long serviceRequestId, Long refundServiceId) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.refundServiceId = refundServiceId;
    }
}
