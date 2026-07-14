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
 * ScheduleRetiredEvent 领域事件。
 */
@Getter
@Setter
public class ScheduleRetiredEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 废弃原因（可选）。 */
    private String reason;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ScheduleRetiredEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long scheduleId, String reason, Long operatorId逻辑。
     * @return 返回结果
     */
    public ScheduleRetiredEvent(Long aggregateId, Long scheduleId, String reason, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SCHEDULE);
        this.scheduleId = scheduleId;
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
