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
 * ScheduleServiceProfileUpdatedEvent 领域事件。
 */
@Getter
@Setter
public class ScheduleServiceProfileUpdatedEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 变更摘要版本号（由应用或服务生成）。 */
    private String profileVersion;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ScheduleServiceProfileUpdatedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long scheduleId, String profileVersion, Long operatorId逻辑。
     * @return 返回结果
     */
    public ScheduleServiceProfileUpdatedEvent(Long aggregateId, Long scheduleId, String profileVersion, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SCHEDULE);
        this.scheduleId = scheduleId;
        this.profileVersion = profileVersion;
        this.operatorId = operatorId;
    }
}
