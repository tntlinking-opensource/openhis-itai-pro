/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.schedule;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * ScheduleCreatedEvent 领域事件。
 */
@Getter
@Setter
public class ScheduleCreatedEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 机构标识。 */
    private Long organizationId;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ScheduleCreatedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long scheduleId, Long organizationId, Long operatorId逻辑。
     * @return 返回结果
     */
    public ScheduleCreatedEvent(Long aggregateId, Long scheduleId, Long organizationId, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SCHEDULE);
        this.scheduleId = scheduleId;
        this.organizationId = organizationId;
        this.operatorId = operatorId;
    }
}
