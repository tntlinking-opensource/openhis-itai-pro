/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.devicedispense;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * DeviceDispenseCreatedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispenseCreatedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 耗材请求标识。 */
    private Long deviceReqId;

    /** 发放状态。 */
    private String statusEnum;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispenseCreatedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, Long deviceReqId, String statusEnum, Long operatorId逻辑。
     * @return 返回结果
     */
    public DeviceDispenseCreatedEvent(Long aggregateId, String busNo, Long deviceReqId, String statusEnum, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.busNo = busNo;
        this.deviceReqId = deviceReqId;
        this.statusEnum = statusEnum;
        this.operatorId = operatorId;
    }
}
