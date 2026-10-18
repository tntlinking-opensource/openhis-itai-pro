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
 * DeviceDefinitionStartEvent。
 */
public class DeviceDefinitionStartEvent extends DomainEvent {
    /**
     * statusEnum。
     */
    private String statusEnum;
    public DeviceDefinitionStartEvent() { super(); }
    /**
     * 处理Long id, String statusEnum逻辑。
     * @return 返回结果
     */
    public DeviceDefinitionStartEvent(Long id, String statusEnum) {
        super(id, AggregateTypeConstants.DEVICE_DEFINITION);
        this.statusEnum = statusEnum;
    }
}
