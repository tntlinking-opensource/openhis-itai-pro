/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.servicedevicetemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗耗材绑定已删除事件（逻辑删除）。
 */
@Getter
@Setter
public class ServiceDeviceTemplateDeletedEvent extends DomainEvent {
    /** 操作人主键（ServiceDeviceTemplateDeletedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ServiceDeviceTemplateDeletedEvent() {
        super();
    }

    /**
     * 处理Long bindingId逻辑。
     * @return 返回结果
     */
    public ServiceDeviceTemplateDeletedEvent(Long bindingId, Long operatorId) {
        super(bindingId, AggregateTypeConstants.SERVICE_DEVICE_TEMPLATE);
        this.operatorId = operatorId;
    }
}
