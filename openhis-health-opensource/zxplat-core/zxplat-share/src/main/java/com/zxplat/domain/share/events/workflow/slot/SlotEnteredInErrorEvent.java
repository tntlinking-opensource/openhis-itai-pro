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
 * SlotEnteredInErrorEvent 领域事件。
 */
@Getter
@Setter
public class SlotEnteredInErrorEvent extends DomainEvent {

    /** 号源时段标识。 */
    private Long slotId;

    /** 原因（可选）。 */
    private String reason;

    /** 操作人主键（SlotEnteredInErrorEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotEnteredInErrorEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long slotId, String reason逻辑。
     * @return 返回结果
     */
    public SlotEnteredInErrorEvent(Long aggregateId, Long slotId, String reason, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.slotId = slotId;
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
