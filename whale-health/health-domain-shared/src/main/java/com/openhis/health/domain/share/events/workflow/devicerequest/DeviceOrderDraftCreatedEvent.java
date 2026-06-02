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

package com.openhis.health.domain.share.events.workflow.devicerequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
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
