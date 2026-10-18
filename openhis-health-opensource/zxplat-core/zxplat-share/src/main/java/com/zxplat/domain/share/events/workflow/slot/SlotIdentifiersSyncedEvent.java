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
 * SlotIdentifiersSyncedEvent 领域事件。
 */
@Getter
@Setter
public class SlotIdentifiersSyncedEvent extends DomainEvent {

    /** 号源时段标识。 */
    private Long slotId;

    /** 标识条数。 */
    private Integer identifierCount;

    /** 操作人主键（SlotIdentifiersSyncedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotIdentifiersSyncedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long slotId, Integer identifierCount逻辑。
     * @return 返回结果
     */
    public SlotIdentifiersSyncedEvent(Long aggregateId, Long slotId, Integer identifierCount, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.slotId = slotId;
        this.identifierCount = identifierCount;
        this.operatorId = operatorId;
    }
}
