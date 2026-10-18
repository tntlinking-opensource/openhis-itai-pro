/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.devicedefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * DeviceDefinitionCreatedEvent。
 */
public class DeviceDefinitionCreatedEvent extends DomainEvent {
    /**
     * busNo。
     */
    private String busNo;
    /**
     * name。
     */
    private String name;
    /**
     * statusEnum。
     */
    private String statusEnum;
    public DeviceDefinitionCreatedEvent() { super(); }
    /**
     * 处理Long id, String busNo, String name, String statusEnum逻辑。
     * @return 返回结果
     */
    public DeviceDefinitionCreatedEvent(Long id, String busNo, String name, String statusEnum) {
        super(id, AggregateTypeConstants.DEVICE_DEFINITION);
        this.busNo = busNo;
        this.name = name;
        this.statusEnum = statusEnum;
    }
}
