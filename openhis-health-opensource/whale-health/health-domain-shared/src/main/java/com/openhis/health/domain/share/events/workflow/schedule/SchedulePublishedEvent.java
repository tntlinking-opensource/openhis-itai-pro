/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.schedule;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * SchedulePublishedEvent 领域事件。
 */
@Getter
@Setter
public class SchedulePublishedEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 规划窗口起。 */
    private java.time.LocalDateTime planningHorizonStart;

    /** 规划窗口止。 */
    private java.time.LocalDateTime planningHorizonEnd;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SchedulePublishedEvent() {
        super();
    }

    public SchedulePublishedEvent(
        Long aggregateId,
        Long scheduleId,
        java.time.LocalDateTime planningHorizonStart,
        java.time.LocalDateTime planningHorizonEnd,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SCHEDULE);
        this.scheduleId = scheduleId;
        this.planningHorizonStart = planningHorizonStart;
        this.planningHorizonEnd = planningHorizonEnd;
        this.operatorId = operatorId;
    }
}
