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
 * 耗材医嘱取消/废弃事件（含草稿 DRAFT→CANCELLED）。
 */
@Getter
@Setter
public class DeviceOrderCancelledEvent extends DomainEvent {

    /** 取消原因。 */
    private String cancelReason;

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 无参构造（反序列化）。
     */
    public DeviceOrderCancelledEvent() {
        super();
    }

    /**
     * 构造废弃事件。
     *
     * @param id           耗材申请主键
     * @param cancelReason 原因
     * @param operatorId   操作人
     */
    public DeviceOrderCancelledEvent(Long id, String cancelReason, Long operatorId) {
        super(id, AggregateTypeConstants.DEVICE_REQUEST);
        this.cancelReason = cancelReason;
        this.operatorId = operatorId;
    }
}
