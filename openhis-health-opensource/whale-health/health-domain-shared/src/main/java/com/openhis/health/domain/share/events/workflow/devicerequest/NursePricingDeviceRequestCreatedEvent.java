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
 * 护士划价耗材请求已创建事件。
 *
 * <p>载荷含 OrderInfo 列表展示所需的耗材定义、数量、单位、库房与患者锚点（对齐 AS-IP-NUR-005 IF-IP-PRC-05）。</p>
 */
@Getter
@Setter
public class NursePricingDeviceRequestCreatedEvent extends DomainEvent {

    /** 关联就诊标识。 */
    private Long encounterId;

    /** 患者标识。 */
    private Long patientId;

    /** 耗材定义标识。 */
    private Long deviceDefId;

    /** 请求数量。 */
    private BigDecimal quantity;

    /** 请求单位 code。 */
    private String unitCode;

    /** 发放库房标识。 */
    private Long performLocation;

    /** 上级医嘱来源表。 */
    private String basedOnTable;

    /** 上级医嘱来源 ID。 */
    private Long basedOnId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public NursePricingDeviceRequestCreatedEvent() {
        super();
    }

    /**
     * 构造护士划价耗材请求已创建事件。
     *
     * @param deviceRequestId 耗材请求主键（aggregateId）
     * @param encounterId     关联就诊标识
     * @param patientId       患者标识
     * @param deviceDefId     耗材定义标识
     * @param quantity        请求数量
     * @param unitCode        请求单位 code
     * @param performLocation 发放库房标识
     * @param basedOnTable    上级医嘱来源表
     * @param basedOnId       上级医嘱来源 ID
     */
    public NursePricingDeviceRequestCreatedEvent(
        Long deviceRequestId,
        Long encounterId,
        Long patientId,
        Long deviceDefId,
        BigDecimal quantity,
        String unitCode,
        Long performLocation,
        String basedOnTable,
        Long basedOnId) {
        super(deviceRequestId, AggregateTypeConstants.DEVICE_REQUEST);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.deviceDefId = deviceDefId;
        this.quantity = quantity;
        this.unitCode = unitCode;
        this.performLocation = performLocation;
        this.basedOnTable = basedOnTable;
        this.basedOnId = basedOnId;
    }
}
