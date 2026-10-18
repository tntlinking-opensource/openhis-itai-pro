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
 * 检查工单已登记确认（进入待摄）事件。
 */
@Getter
@Setter
public class PacsWorkItemScheduledEvent extends DomainEvent {

    /** 检查号。 */
    private String accessionNumber;

    /** 检查执行态枚举 code。 */
    private String statusEnum;

    /** 无参构造器（反序列化用）。 */
    public PacsWorkItemScheduledEvent() {
        super();
    }

    /**
     * @param workItemId 工单主键（聚合根 ID）
     * @param accessionNumber 检查号
     * @param statusEnum 执行态
     */
    public PacsWorkItemScheduledEvent(Long workItemId, String accessionNumber, String statusEnum) {
        super(workItemId, AggregateTypeConstants.PACS_WORK_ITEM);
        this.accessionNumber = accessionNumber;
        this.statusEnum = statusEnum;
    }
}
