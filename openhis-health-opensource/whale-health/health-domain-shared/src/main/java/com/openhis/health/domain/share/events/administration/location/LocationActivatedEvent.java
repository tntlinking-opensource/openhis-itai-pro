/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.location;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 位置已启用事件。
 */
@Getter
@Setter
public class LocationActivatedEvent extends DomainEvent {
    /** name（LocationActivatedEvent）。 */
    private String name;
    /** form编码（LocationActivatedEvent）。 */
    private String formEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public LocationActivatedEvent() {
        super();
    }

    /**
     * 处理Long locationId, String name, Integer formEnum逻辑。
     * @return 返回结果
     */
    public LocationActivatedEvent(Long locationId, String name, String formEnum) {
        super(locationId, AggregateTypeConstants.LOCATION);
        this.name = name;
        this.formEnum = formEnum;
    }
}
