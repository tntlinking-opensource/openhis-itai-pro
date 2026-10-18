/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.location;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 位置已暂停事件。
 */
@Getter
@Setter
public class LocationSuspendedEvent extends DomainEvent {
    /** 原因（LocationSuspendedEvent）。 */
    private String reason;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public LocationSuspendedEvent() {
        super();
    }

    /**
     * 处理Long locationId, String reason逻辑。
     * @return 返回结果
     */
    public LocationSuspendedEvent(Long locationId, String reason) {
        super(locationId, AggregateTypeConstants.LOCATION);
        this.reason = reason;
    }
}
