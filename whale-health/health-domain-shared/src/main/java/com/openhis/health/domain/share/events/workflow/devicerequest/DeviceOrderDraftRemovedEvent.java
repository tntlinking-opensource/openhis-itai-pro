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
 * 耗材草稿已删除事件。
 *
 * <p>SDD-WKF-003 §7 最小载荷：deviceRequestId（via super.aggregateId）、encounterId。</p>
 */
@Getter
@Setter
public class DeviceOrderDraftRemovedEvent extends DomainEvent {

    /** 关联就诊标识。 */
    private Long encounterId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public DeviceOrderDraftRemovedEvent() {
        super();
    }

    /**
     * 构造耗材草稿已删除事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param encounterId     关联就诊标识
     */
    public DeviceOrderDraftRemovedEvent(Long deviceRequestId, Long encounterId) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.encounterId = encounterId;
    }
}
