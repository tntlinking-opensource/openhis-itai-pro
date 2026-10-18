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
 * 床位已占用事件。
 */
@Getter
@Setter
public class BedOccupiedEvent extends DomainEvent {
    /** 就诊主键（BedOccupiedEvent）。 */
    private Long encounterId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public BedOccupiedEvent() {
        super();
    }

    /**
     * 处理Long locationId, Long encounterId逻辑。
     * @return 返回结果
     */
    public BedOccupiedEvent(Long locationId, Long encounterId) {
        super(locationId, AggregateTypeConstants.LOCATION);
        this.encounterId = encounterId;
    }
}
