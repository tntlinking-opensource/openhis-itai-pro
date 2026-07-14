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
 * SchedulePlanningHorizonChangedEvent 领域事件。
 */
@Getter
@Setter
public class SchedulePlanningHorizonChangedEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 旧窗口起。 */
    private java.time.LocalDateTime oldStart;

    /** 旧窗口止。 */
    private java.time.LocalDateTime oldEnd;

    /** 新窗口起。 */
    private java.time.LocalDateTime newStart;

    /** 新窗口止。 */
    private java.time.LocalDateTime newEnd;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SchedulePlanningHorizonChangedEvent() {
        super();
    }

    public SchedulePlanningHorizonChangedEvent(
        Long aggregateId,
        Long scheduleId,
        java.time.LocalDateTime oldStart,
        java.time.LocalDateTime oldEnd,
        java.time.LocalDateTime newStart,
        java.time.LocalDateTime newEnd,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SCHEDULE);
        this.scheduleId = scheduleId;
        this.oldStart = oldStart;
        this.oldEnd = oldEnd;
        this.newStart = newStart;
        this.newEnd = newEnd;
        this.operatorId = operatorId;
    }
}
