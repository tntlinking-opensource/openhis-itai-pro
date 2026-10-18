/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.servicerequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗服务申请已取消停嘱领域事件（IF-IP-DOR-25）。
 */
@Getter
@Setter
public class ServiceRequestStopCancelledEvent extends DomainEvent {

    /** 取消停嘱后请求状态（{@link com.zxplat.domain.share.enums.RequestStatus#getCode()}）。 */
    private String statusEnum;

    /** 无参构造，供序列化框架使用。 */
    public ServiceRequestStopCancelledEvent() {
        super();
    }

    /**
     * 构造取消停嘱事件载荷。
     *
     * @param serviceRequestId 服务申请主键
     * @param statusEnum       恢复后的状态 code
     */
    public ServiceRequestStopCancelledEvent(Long serviceRequestId, String statusEnum) {
        super(serviceRequestId, AggregateTypeConstants.SERVICE_REQUEST);
        this.statusEnum = statusEnum;
    }
}
