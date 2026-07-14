/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.slot;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * SlotUnblockedEvent 领域事件。
 */
@Getter
@Setter
public class SlotUnblockedEvent extends DomainEvent {

    /** 号源时段标识。 */
    private Long slotId;

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 解封后的状态码。 */
    private String statusCode;

    /** 操作人主键（SlotUnblockedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotUnblockedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long slotId, Long scheduleId, String statusCode逻辑。
     * @return 返回结果
     */
    public SlotUnblockedEvent(Long aggregateId, Long slotId, Long scheduleId, String statusCode, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.slotId = slotId;
        this.scheduleId = scheduleId;
        this.statusCode = statusCode;
        this.operatorId = operatorId;
    }
}
