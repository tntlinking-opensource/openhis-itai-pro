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
 * SlotTentativeMarkedEvent 领域事件。
 */
@Getter
@Setter
public class SlotTentativeMarkedEvent extends DomainEvent {

    /** 号源时段标识。 */
    private Long slotId;

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 操作人主键（SlotTentativeMarkedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotTentativeMarkedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long slotId, Long scheduleId逻辑。
     * @return 返回结果
     */
    public SlotTentativeMarkedEvent(Long aggregateId, Long slotId, Long scheduleId, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.slotId = slotId;
        this.scheduleId = scheduleId;
        this.operatorId = operatorId;
    }
}
