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
 * SlotOverbookedFlagSetEvent 领域事件。
 */
@Getter
@Setter
public class SlotOverbookedFlagSetEvent extends DomainEvent {

    /** 号源时段标识。 */
    private Long slotId;

    /** 超售标记。 */
    private Boolean overbookedFlag;

    /** 操作人主键（SlotOverbookedFlagSetEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotOverbookedFlagSetEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long slotId, Boolean overbookedFlag逻辑。
     * @return 返回结果
     */
    public SlotOverbookedFlagSetEvent(Long aggregateId, Long slotId, Boolean overbookedFlag, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.slotId = slotId;
        this.overbookedFlag = overbookedFlag;
        this.operatorId = operatorId;
    }
}
