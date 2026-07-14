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
 * DeviceDispenseReserveReleasedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispenseReserveReleasedEvent extends DomainEvent {

    /** 发放标识列表或请求维度键（以落库与交互契约为准）。 */
    private String idListOrRequestKey;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispenseReserveReleasedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String idListOrRequestKey, Long operatorId逻辑。
     * @return 返回结果
     */
    public DeviceDispenseReserveReleasedEvent(Long aggregateId, String idListOrRequestKey, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.idListOrRequestKey = idListOrRequestKey;
        this.operatorId = operatorId;
    }
}
