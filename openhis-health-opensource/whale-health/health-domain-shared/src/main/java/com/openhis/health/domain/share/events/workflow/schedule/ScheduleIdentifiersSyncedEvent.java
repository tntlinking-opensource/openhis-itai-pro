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
 * ScheduleIdentifiersSyncedEvent 领域事件。
 */
@Getter
@Setter
public class ScheduleIdentifiersSyncedEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 标识条数。 */
    private Integer identifierCount;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ScheduleIdentifiersSyncedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long scheduleId, Integer identifierCount, Long operatorId逻辑。
     * @return 返回结果
     */
    public ScheduleIdentifiersSyncedEvent(Long aggregateId, Long scheduleId, Integer identifierCount, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SCHEDULE);
        this.scheduleId = scheduleId;
        this.identifierCount = identifierCount;
        this.operatorId = operatorId;
    }
}
