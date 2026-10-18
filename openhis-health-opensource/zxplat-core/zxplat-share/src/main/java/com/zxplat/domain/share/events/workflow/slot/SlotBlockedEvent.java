/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.slot;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * SlotBlockedEvent 领域事件。
 */
@Getter
@Setter
public class SlotBlockedEvent extends DomainEvent {

    /** 号源时段标识。 */
    private Long slotId;

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 开始时刻。 */
    private java.time.LocalDateTime startTime;

    /** 结束时刻。 */
    private java.time.LocalDateTime endTime;

    /** 操作人主键（SlotBlockedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotBlockedEvent() {
        super();
    }

    public SlotBlockedEvent(
        Long aggregateId,
        Long slotId,
        Long scheduleId,
        java.time.LocalDateTime startTime,
        java.time.LocalDateTime endTime) {
        this(aggregateId, slotId, scheduleId, startTime, endTime, null);
    }

    public SlotBlockedEvent(
        Long aggregateId,
        Long slotId,
        Long scheduleId,
        java.time.LocalDateTime startTime,
        java.time.LocalDateTime endTime,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.slotId = slotId;
        this.scheduleId = scheduleId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.operatorId = operatorId;
    }
}
