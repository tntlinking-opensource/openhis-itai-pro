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

/**
 * 门诊耗材发放已确认事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、performerId、performOrgId。</p>
 */
@Getter
@Setter
public class OutpatientDeviceDispenseConfirmedEvent extends DomainEvent {

    /** 执行人标识。 */
    private Long performerId;

    /** 执行机构标识。 */
    private Long performOrgId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public OutpatientDeviceDispenseConfirmedEvent() {
        super();
    }

    /**
     * 构造门诊耗材发放已确认事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param performerId     执行人标识
     * @param performOrgId    执行机构标识
     */
    public OutpatientDeviceDispenseConfirmedEvent(Long deviceRequestId, Long performerId, Long performOrgId) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.performerId = performerId;
        this.performOrgId = performOrgId;
    }
}
