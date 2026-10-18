/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.servicerequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
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

    /** 停嘱后请求状态（{@link com.zxplat.domain.share.enums.RequestStatus#getCode()}，如 STOPPED）。 */
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
