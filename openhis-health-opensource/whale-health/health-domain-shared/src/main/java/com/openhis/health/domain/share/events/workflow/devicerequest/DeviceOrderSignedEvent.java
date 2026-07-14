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
 * 耗材请求已签发事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、status（签发后状态枚举）。</p>
 */
@Getter
@Setter
public class DeviceOrderSignedEvent extends DomainEvent {

    /** 签发后状态枚举。 */
    private String status;

    /**
     * 默认构造函数（反序列化用）。
     */
    public DeviceOrderSignedEvent() {
        super();
    }

    /**
     * 构造耗材请求已签发事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param status          签发后状态枚举
     */
    public DeviceOrderSignedEvent(Long deviceRequestId, String status) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.status = status;
    }
}
