/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.supplydelivery;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 供应发放已创建事件（DM-WKF-005 §5.1 事件 #1）。
 *
 * <p>触发时机：各 create* 方法落库成功后。</p>
 */
@Getter
@Setter
public class SupplyDeliveryCreatedEvent extends DomainEvent {

    /** 供应请求标识。 */
    private Long requestId;

    /** 单据类型枚举。 */
    private String typeEnum;

    /** 初始状态枚举。 */
    private String statusEnum;

    /** 发放数量。 */
    private BigDecimal quantity;

    /**
     * 无参构造（反序列化用）。
     */
    public SupplyDeliveryCreatedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId 供应发放标识
     * @param requestId   供应请求标识
     * @param typeEnum    单据类型枚举
     * @param statusEnum  初始状态枚举
     * @param quantity    发放数量
     */
    public SupplyDeliveryCreatedEvent(Long aggregateId, Long requestId, String typeEnum,
                                      String statusEnum, BigDecimal quantity) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_DELIVERY);
        this.requestId = requestId;
        this.typeEnum = typeEnum;
        this.statusEnum = statusEnum;
        this.quantity = quantity;
    }
}
