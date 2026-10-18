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
 * DeviceDispensePendingRefundCreatedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispensePendingRefundCreatedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 原发放标识。 */
    private Long sourceDispenseId;

    /** 退耗材数量。 */
    private java.math.BigDecimal refundQuantity;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispensePendingRefundCreatedEvent() {
        super();
    }

    public DeviceDispensePendingRefundCreatedEvent(
        Long aggregateId,
        String busNo,
        Long sourceDispenseId,
        java.math.BigDecimal refundQuantity,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.busNo = busNo;
        this.sourceDispenseId = sourceDispenseId;
        this.refundQuantity = refundQuantity;
        this.operatorId = operatorId;
    }
}
