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
 * ScheduleActorsReplacedEvent 领域事件。
 */
@Getter
@Setter
public class ScheduleActorsReplacedEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 参与者数量。 */
    private Integer actorCount;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ScheduleActorsReplacedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long scheduleId, Integer actorCount, Long operatorId逻辑。
     * @return 返回结果
     */
    public ScheduleActorsReplacedEvent(Long aggregateId, Long scheduleId, Integer actorCount, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SCHEDULE);
        this.scheduleId = scheduleId;
        this.actorCount = actorCount;
        this.operatorId = operatorId;
    }
}
