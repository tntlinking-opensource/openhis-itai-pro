/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.location;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;

/**
 * 位置已恢复事件。
 */
public class LocationResumedEvent extends DomainEvent {

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public LocationResumedEvent() {
        super();
    }

    /**
     * 处理Long locationId逻辑。
     * @return 返回结果
     */
    public LocationResumedEvent(Long locationId) {
        super(locationId, AggregateTypeConstants.LOCATION);
    }
}
