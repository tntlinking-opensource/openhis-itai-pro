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

package com.openhis.health.domain.share.events.workflow.devicedispense;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * DeviceDispensedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispensedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 发放数量。 */
    private java.math.BigDecimal dispenseQuantity;

    /** 批号。 */
    private String lotNumber;

    /** 追溯码。 */
    private String traceNo;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispensedEvent() {
        super();
    }

    public DeviceDispensedEvent(
        Long aggregateId,
        String busNo,
        java.math.BigDecimal dispenseQuantity,
        String lotNumber,
        String traceNo,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.busNo = busNo;
        this.dispenseQuantity = dispenseQuantity;
        this.lotNumber = lotNumber;
        this.traceNo = traceNo;
        this.operatorId = operatorId;
    }
}
