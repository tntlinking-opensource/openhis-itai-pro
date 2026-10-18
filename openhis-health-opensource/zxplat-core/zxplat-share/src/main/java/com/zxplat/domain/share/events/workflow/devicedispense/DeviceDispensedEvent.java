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
 * DeviceDispensedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispensedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 发放数量。 */
    private java.math.BigDecimal dispenseQuantity;

    /** 批号。 */
    private String lotNumber;

    /** 追溯码。 */
    private String traceNo;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispensedEvent() {
        super();
    }

    public DeviceDispensedEvent(
        Long aggregateId,
        String busNo,
        java.math.BigDecimal dispenseQuantity,
        String lotNumber,
        String traceNo,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.busNo = busNo;
        this.dispenseQuantity = dispenseQuantity;
        this.lotNumber = lotNumber;
        this.traceNo = traceNo;
        this.operatorId = operatorId;
    }
}
