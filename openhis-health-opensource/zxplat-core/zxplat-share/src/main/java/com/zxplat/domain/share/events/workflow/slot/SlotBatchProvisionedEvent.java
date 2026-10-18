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
 * SlotBatchProvisionedEvent 领域事件。
 */
@Getter
@Setter
public class SlotBatchProvisionedEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 生成条数。 */
    private Integer generatedCount;

    /** 新建时段标识列表（可用于下游预热/索引）。 */
    private java.util.List<Long> slotIdList;

    /** 操作人主键（SlotBatchProvisionedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotBatchProvisionedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long scheduleId, Integer generatedCount, java.util.List<Long> slotIdList逻辑。
     * @return 返回结果
     */
    public SlotBatchProvisionedEvent(Long aggregateId, Long scheduleId, Integer generatedCount, java.util.List<Long> slotIdList, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.scheduleId = scheduleId;
        this.generatedCount = generatedCount;
        this.slotIdList = slotIdList;
        this.operatorId = operatorId;
    }
}
