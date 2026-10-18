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
 * 门诊耗材发放已确认事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、performerId、performOrgId。</p>
 */
@Getter
@Setter
public class OutpatientDeviceDispenseConfirmedEvent extends DomainEvent {

    /** 执行人标识。 */
    private Long performerId;

    /** 执行机构标识。 */
    private Long performOrgId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public OutpatientDeviceDispenseConfirmedEvent() {
        super();
    }

    /**
     * 构造门诊耗材发放已确认事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param performerId     执行人标识
     * @param performOrgId    执行机构标识
     */
    public OutpatientDeviceDispenseConfirmedEvent(Long deviceRequestId, Long performerId, Long performOrgId) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.performerId = performerId;
        this.performOrgId = performOrgId;
    }
}
