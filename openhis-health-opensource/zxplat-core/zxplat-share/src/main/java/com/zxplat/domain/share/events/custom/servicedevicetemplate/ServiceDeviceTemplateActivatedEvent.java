/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.servicedevicetemplate;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗耗材绑定已启用事件。
 */
@Getter
@Setter
public class ServiceDeviceTemplateActivatedEvent extends DomainEvent {
    /** 操作人主键（ServiceDeviceTemplateActivatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ServiceDeviceTemplateActivatedEvent() {
        super();
    }

    /**
     * 处理Long bindingId逻辑。
     * @return 返回结果
     */
    public ServiceDeviceTemplateActivatedEvent(Long bindingId, Long operatorId) {
        super(bindingId, AggregateTypeConstants.SERVICE_DEVICE_TEMPLATE);
        this.operatorId = operatorId;
    }
}
