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
 * 门诊耗材发放确认已撤销事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）。</p>
 */
@Getter
@Setter
public class OutpatientDeviceDispenseConfirmationCancelledEvent extends DomainEvent {

    /**
     * 默认构造函数（反序列化用）。
     */
    public OutpatientDeviceDispenseConfirmationCancelledEvent() {
        super();
    }

    /**
     * 构造门诊耗材发放确认已撤销事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     */
    public OutpatientDeviceDispenseConfirmationCancelledEvent(Long deviceRequestId) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
    }
}
