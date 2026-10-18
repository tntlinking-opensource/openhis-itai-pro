/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.devicerequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 耗材请求已签退事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、status（签退后状态枚举）。</p>
 */
@Getter
@Setter
public class DeviceOrderSignOffEvent extends DomainEvent {

    /** 签退后状态枚举。 */
    private String status;

    /**
     * 默认构造函数（反序列化用）。
     */
    public DeviceOrderSignOffEvent() {
        super();
    }

    /**
     * 构造耗材请求已签退事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param status          签退后状态枚举
     */
    public DeviceOrderSignOffEvent(Long deviceRequestId, String status) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.status = status;
    }
}
