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
 * 诊疗耗材绑定已停用事件。
 */
@Getter
@Setter
public class ServiceDeviceTemplateRetiredEvent extends DomainEvent {
    /** 操作人主键（ServiceDeviceTemplateRetiredEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ServiceDeviceTemplateRetiredEvent() {
        super();
    }

    /**
     * 处理Long bindingId逻辑。
     * @return 返回结果
     */
    public ServiceDeviceTemplateRetiredEvent(Long bindingId, Long operatorId) {
        super(bindingId, AggregateTypeConstants.SERVICE_DEVICE_TEMPLATE);
        this.operatorId = operatorId;
    }
}
