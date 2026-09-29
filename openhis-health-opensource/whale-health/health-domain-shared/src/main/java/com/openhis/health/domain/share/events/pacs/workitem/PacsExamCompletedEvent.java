/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.pacs.workitem;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查已完成事件。
 */
@Getter
@Setter
public class PacsExamCompletedEvent extends DomainEvent {

    /** 检查号。 */
    private String accessionNumber;

    /** 实际完成时间。 */
    private LocalDateTime performedTime;

    /** 检查执行态枚举 code。 */
    private String statusEnum;

    /** 无参构造器（反序列化用）。 */
    public PacsExamCompletedEvent() {
        super();
    }

    /**
     * @param workItemId 工单主键（聚合根 ID）
     * @param accessionNumber 检查号
     * @param performedTime 完成时间
     * @param statusEnum 执行态
     */
    public PacsExamCompletedEvent(
        Long workItemId,
        String accessionNumber,
        LocalDateTime performedTime,
        String statusEnum) {
        super(workItemId, AggregateTypeConstants.PACS_WORK_ITEM);
        this.accessionNumber = accessionNumber;
        this.performedTime = performedTime;
        this.statusEnum = statusEnum;
    }
}
