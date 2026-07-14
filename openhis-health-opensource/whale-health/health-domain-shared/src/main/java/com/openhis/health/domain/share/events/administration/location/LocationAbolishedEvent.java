/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.location;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;

/**
 * 位置已废弃事件。
 */
public class LocationAbolishedEvent extends DomainEvent {

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public LocationAbolishedEvent() {
        super();
    }

    /**
     * 处理Long locationId逻辑。
     * @return 返回结果
     */
    public LocationAbolishedEvent(Long locationId) {
        super(locationId, AggregateTypeConstants.LOCATION);
    }
}
