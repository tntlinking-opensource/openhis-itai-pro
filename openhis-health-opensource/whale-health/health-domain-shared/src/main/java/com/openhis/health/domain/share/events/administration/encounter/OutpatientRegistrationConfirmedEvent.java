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

package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊挂号缴费确认领域事件。
 *
 * <p>就诊聚合在写侧完成「待挂号 → 已挂号」状态迁移并成功持久化后发布；与支付域
 * {@link com.openhis.health.domain.share.events.financial.paymentreconciliation.OutpatientPaymentSettledEvent}
 * 解耦，避免普通门诊收费结算误推进就诊展示状态。读模型投影须从本事件载荷读取 {@link #statusEnum}，
 * 展示文案经 {@link com.openhis.health.domain.share.utils.EnumUtil} 解析枚举后取得，禁止在投影器内编造状态码。</p>
 */
@Getter
@Setter
public class OutpatientRegistrationConfirmedEvent extends DomainEvent {

    /** 患者主键，供下游按患者维度关联或校验。 */
    private Long patientId;

    /**
     * 确认生效后的就诊状态持久化码（与 {@code adm_encounter.status_enum} 及 {@link com.openhis.health.domain.share.enums.EncounterStatus#getCode()} 一致）。
     */
    private String statusEnum;

    /**
     * 框架反序列化用无参构造。
     */
    public OutpatientRegistrationConfirmedEvent() {
        super();
    }

    /**
     * 构造挂号确认事件（发布前须补全 {@link #statusEnum}）。
     *
     * @param encounterId 就诊聚合主键，作为领域事件 aggregateId
     * @param patientId     患者主键
     */
    public OutpatientRegistrationConfirmedEvent(Long encounterId, Long patientId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
    }
}
