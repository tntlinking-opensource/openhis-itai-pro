/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.devicedispense;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * DeviceDispenseSummaryCompletedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispenseSummaryCompletedEvent extends DomainEvent {

    /** 汇总锚点（以落库与交互契约为准，可能为业务号或批次号）。 */
    private String batchKey;

    /** 发放标识列表。 */
    private java.util.List<Long> idList;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispenseSummaryCompletedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String batchKey, java.util.List<Long> idList, Long operatorId逻辑。
     * @return 返回结果
     */
    public DeviceDispenseSummaryCompletedEvent(Long aggregateId, String batchKey, java.util.List<Long> idList, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.batchKey = batchKey;
        this.idList = idList;
        this.operatorId = operatorId;
    }
}
