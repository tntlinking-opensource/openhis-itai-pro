/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.devicerequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 耗材草稿已创建事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、deviceDefId、encounterId、quantity。</p>
 */
@Getter
@Setter
public class DeviceOrderDraftCreatedEvent extends DomainEvent {

    /** 耗材定义标识。 */
    private Long deviceDefId;

    /** 关联就诊标识。 */
    private Long encounterId;

    /** 申请数量。 */
    private BigDecimal quantity;

    /**
     * 默认构造函数（反序列化用）。
     */
    public DeviceOrderDraftCreatedEvent() {
        super();
    }

    /**
     * 构造耗材草稿已创建事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param deviceDefId     耗材定义标识
     * @param encounterId     关联就诊标识
     * @param quantity        申请数量
     */
    public DeviceOrderDraftCreatedEvent(Long deviceRequestId, Long deviceDefId, Long encounterId, BigDecimal quantity) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.deviceDefId = deviceDefId;
        this.encounterId = encounterId;
        this.quantity = quantity;
    }
}
