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
 * 床位已预留事件。
 */
@Getter
@Setter
public class BedReservedEvent extends DomainEvent {
    /** 原因（BedReservedEvent）。 */
    private String reason;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public BedReservedEvent() {
        super();
    }

    /**
     * 处理Long locationId, String reason逻辑。
     * @return 返回结果
     */
    public BedReservedEvent(Long locationId, String reason) {
        super(locationId, AggregateTypeConstants.LOCATION);
        this.reason = reason;
    }
}
