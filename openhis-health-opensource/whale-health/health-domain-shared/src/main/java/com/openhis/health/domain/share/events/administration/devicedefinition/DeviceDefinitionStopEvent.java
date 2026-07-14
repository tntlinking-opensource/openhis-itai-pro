/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.devicedefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * DeviceDefinitionStopEvent。
 */
public class DeviceDefinitionStopEvent extends DomainEvent {
    /**
     * statusEnum。
     */
    private String statusEnum;
    public DeviceDefinitionStopEvent() { super(); }
    /**
     * 处理Long id, String statusEnum逻辑。
     * @return 返回结果
     */
    public DeviceDefinitionStopEvent(Long id, String statusEnum) {
        super(id, AggregateTypeConstants.DEVICE_DEFINITION);
        this.statusEnum = statusEnum;
    }
}
