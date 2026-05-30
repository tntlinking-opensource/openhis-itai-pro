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

package com.openhis.health.domain.share.events.financial.paymentreconciliation;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 支付草稿已创建事件。
 *
 * <p>触发时机：预结算生成支付草稿后。</p>
 */
@Getter
@Setter
public class PaymentDraftCreatedEvent extends DomainEvent {

    /** 就诊标识。 */
    private Long encounterId;

    /** 患者标识。 */
    private Long patientId;

    /** 收费明细标识集合（JSON 数组字符串）。 */
    private String chargeItemIds;

    /** 应收金额。 */
    private BigDecimal displayAmount;

    /**
     * 无参构造（反序列化用）。
     */
    public PaymentDraftCreatedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId   支付流水标识
     * @param encounterId   就诊标识
     * @param patientId     患者标识
     * @param chargeItemIds 收费明细标识集合
     * @param displayAmount 应收金额
     */
    public PaymentDraftCreatedEvent(Long aggregateId, Long encounterId, Long patientId,
                                    String chargeItemIds, BigDecimal displayAmount) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.chargeItemIds = chargeItemIds;
        this.displayAmount = displayAmount;
    }
}
