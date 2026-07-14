/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.devicerequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 耗材退货已申请事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、refundDeviceId（原耗材请求主键）。</p>
 */
@Getter
@Setter
public class DeviceOrderRefundRequestedEvent extends DomainEvent {

    /** 原耗材请求主键（退货冲销来源）。 */
    private Long refundDeviceId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public DeviceOrderRefundRequestedEvent() {
        super();
    }

    /**
     * 构造耗材退货已申请事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param refundDeviceId  原耗材请求主键
     */
    public DeviceOrderRefundRequestedEvent(Long deviceRequestId, Long refundDeviceId) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.refundDeviceId = refundDeviceId;
    }
}
