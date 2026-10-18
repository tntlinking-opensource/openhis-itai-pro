/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.devicedispense;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * DeviceDispensePreparedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispensePreparedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 配药人标识。 */
    private Long preparerId;

    /** 配药时间。 */
    private java.time.LocalDateTime prepareTime;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispensePreparedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, Long preparerId, java.time.LocalDateTime prepareTime, Long operatorId逻辑。
     * @return 返回结果
     */
    public DeviceDispensePreparedEvent(Long aggregateId, String busNo, Long preparerId, java.time.LocalDateTime prepareTime, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.busNo = busNo;
        this.preparerId = preparerId;
        this.prepareTime = prepareTime;
        this.operatorId = operatorId;
    }
}
