/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.clinical.observation;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 临床观察实例已确认（final）事件（DM-CLN-007 finalizeObservation 后发布）。
 */
@Getter
@Setter
public class ObservationFinalizedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 观察定义主键。 */
    private Long definitionId;

    /**
     * 构造已确认事件。
     *
     * @param id               观察实例主键
     * @param encounterId      就诊主键
     * @param serviceRequestId 服务申请主键
     * @param definitionId     观察定义主键
     */
    public ObservationFinalizedEvent(
        Long id, Long encounterId, Long serviceRequestId, Long definitionId) {
        super(id, AggregateTypeConstants.OBSERVATION);
        this.encounterId = encounterId;
        this.serviceRequestId = serviceRequestId;
        this.definitionId = definitionId;
    }
}
