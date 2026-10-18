/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.observation;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 临床观察实例已作废事件（DM-CLN-007 cancelObservation 后发布）。
 */
@Getter
@Setter
public class ObservationCancelledEvent extends DomainEvent {

    /** 作废原因摘要。 */
    private String reason;

    /**
     * 构造已作废事件。
     *
     * @param id     观察实例主键
     * @param reason 作废原因
     */
    public ObservationCancelledEvent(Long id, String reason) {
        super(id, AggregateTypeConstants.OBSERVATION);
        this.reason = reason;
    }
}
