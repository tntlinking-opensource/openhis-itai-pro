/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.supplyrequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * SummarySupplyRequestCancelledEvent 领域事件。
 */
@Getter
@Setter
public class SummarySupplyRequestCancelledEvent extends DomainEvent {

    /** 汇总编号列表。 */
    private java.util.List<Long> summaryIdList;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SummarySupplyRequestCancelledEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, java.util.List<Long> summaryIdList, Long operatorId逻辑。
     * @return 返回结果
     */
    public SummarySupplyRequestCancelledEvent(Long aggregateId, java.util.List<Long> summaryIdList, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_REQUEST);
        this.summaryIdList = summaryIdList;
        this.operatorId = operatorId;
    }
}
