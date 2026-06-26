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
 * 护士划价耗材请求已逻辑删除事件。
 *
 * <p>与 {@link NursePricingDeviceRequestCreatedEvent} 对称；供已划价列表等读侧剔除投影（AS-IP-NUR-005 IF-IP-PRC-06）。</p>
 */
@Getter
@Setter
public class NursePricingDeviceRequestRemovedEvent extends DomainEvent {

    /** 关联就诊标识。 */
    private Long encounterId;

    /** 上级医嘱来源表。 */
    private String basedOnTable;

    /** 上级医嘱来源 ID。 */
    private Long basedOnId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public NursePricingDeviceRequestRemovedEvent() {
        super();
    }

    /**
     * 构造护士划价耗材请求已删除事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param encounterId       关联就诊标识
     * @param basedOnTable      上级医嘱来源表
     * @param basedOnId         上级医嘱来源 ID
     */
    public NursePricingDeviceRequestRemovedEvent(
        Long deviceRequestId,
        Long encounterId,
        String basedOnTable,
        Long basedOnId) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.encounterId = encounterId;
        this.basedOnTable = basedOnTable;
        this.basedOnId = basedOnId;
    }
}
