/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.pacs.workitem;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 检查工单已作废事件。
 */
@Getter
@Setter
public class PacsWorkItemCancelledEvent extends DomainEvent {

    /** 作废原因。 */
    private String reason;

    /** 检查执行态枚举 code。 */
    private String statusEnum;

    /** 无参构造器（反序列化用）。 */
    public PacsWorkItemCancelledEvent() {
        super();
    }

    /**
     * @param workItemId 工单主键（聚合根 ID）
     * @param reason 作废原因
     * @param statusEnum 执行态
     */
    public PacsWorkItemCancelledEvent(Long workItemId, String reason, String statusEnum) {
        super(workItemId, AggregateTypeConstants.PACS_WORK_ITEM);
        this.reason = reason;
        this.statusEnum = statusEnum;
    }
}
