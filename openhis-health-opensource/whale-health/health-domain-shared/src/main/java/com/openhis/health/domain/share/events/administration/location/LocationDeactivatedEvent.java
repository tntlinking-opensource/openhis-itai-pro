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
 * 位置已停用事件。
 */
@Getter
@Setter
public class LocationDeactivatedEvent extends DomainEvent {
    /** 原因（LocationDeactivatedEvent）。 */
    private String reason;
    /** form编码（LocationDeactivatedEvent）。 */
    private String formEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public LocationDeactivatedEvent() {
        super();
    }

    /**
     * 处理Long locationId, String reason, Integer formEnum逻辑。
     * @return 返回结果
     */
    public LocationDeactivatedEvent(Long locationId, String reason, String formEnum) {
        super(locationId, AggregateTypeConstants.LOCATION);
        this.reason = reason;
        this.formEnum = formEnum;
    }
}
