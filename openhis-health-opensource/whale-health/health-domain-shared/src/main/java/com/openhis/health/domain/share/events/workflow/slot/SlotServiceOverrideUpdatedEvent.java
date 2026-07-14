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
 * SlotServiceOverrideUpdatedEvent 领域事件。
 */
@Getter
@Setter
public class SlotServiceOverrideUpdatedEvent extends DomainEvent {

    /** 号源时段标识。 */
    private Long slotId;

    /** 操作人主键（SlotServiceOverrideUpdatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotServiceOverrideUpdatedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long slotId逻辑。
     * @return 返回结果
     */
    public SlotServiceOverrideUpdatedEvent(Long aggregateId, Long slotId, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.slotId = slotId;
        this.operatorId = operatorId;
    }
}
