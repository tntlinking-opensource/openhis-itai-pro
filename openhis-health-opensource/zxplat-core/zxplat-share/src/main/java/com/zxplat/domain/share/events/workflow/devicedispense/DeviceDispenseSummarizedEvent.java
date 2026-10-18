/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.devicedispense;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * DeviceDispenseSummarizedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispenseSummarizedEvent extends DomainEvent {

    /** 汇总锚点（以落库与交互契约为准，可能为业务号或批次号）。 */
    private String batchKey;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispenseSummarizedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String batchKey, Long operatorId逻辑。
     * @return 返回结果
     */
    public DeviceDispenseSummarizedEvent(Long aggregateId, String batchKey, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.batchKey = batchKey;
        this.operatorId = operatorId;
    }
}
