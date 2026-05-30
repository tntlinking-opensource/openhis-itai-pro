/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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

