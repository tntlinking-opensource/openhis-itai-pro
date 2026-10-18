/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.devicedefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
/**
 * DeviceDefinitionUpdateEvent。
 */
public class DeviceDefinitionUpdateEvent extends DomainEvent {
    /**
     * changedFields。
     */
    private List<String> changedFields;
    public DeviceDefinitionUpdateEvent() { super(); }
    /**
     * 处理Long id, List<String> changedFields逻辑。
     * @return 返回结果
     */
    public DeviceDefinitionUpdateEvent(Long id, List<String> changedFields) {
        super(id, AggregateTypeConstants.DEVICE_DEFINITION);
        this.changedFields = changedFields;
    }
}
