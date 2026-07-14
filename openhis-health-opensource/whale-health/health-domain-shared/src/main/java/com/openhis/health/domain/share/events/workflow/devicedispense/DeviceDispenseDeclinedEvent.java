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
 * DeviceDispenseDeclinedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispenseDeclinedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 不执行原因。 */
    private String notPerformedReasonEnum;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispenseDeclinedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, String notPerformedReasonEnum, Long operatorId逻辑。
     * @return 返回结果
     */
    public DeviceDispenseDeclinedEvent(Long aggregateId, String busNo, String notPerformedReasonEnum, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.busNo = busNo;
        this.notPerformedReasonEnum = notPerformedReasonEnum;
        this.operatorId = operatorId;
    }
}
