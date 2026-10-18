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
 * SlotTentativeConfirmedEvent 领域事件。
 */
@Getter
@Setter
public class SlotTentativeConfirmedEvent extends DomainEvent {

    /** 号源时段标识。 */
    private Long slotId;

    /** 已预约计数。 */
    private Integer bookedCount;

    /** 状态码。 */
    private String statusCode;

    /** 操作人主键（SlotTentativeConfirmedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotTentativeConfirmedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long slotId, Integer bookedCount, String statusCode逻辑。
     * @return 返回结果
     */
    public SlotTentativeConfirmedEvent(Long aggregateId, Long slotId, Integer bookedCount, String statusCode, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.slotId = slotId;
        this.bookedCount = bookedCount;
        this.statusCode = statusCode;
        this.operatorId = operatorId;
    }
}
