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
 * 门诊退费已申请事件。
 *
 * <p>触发时机：门诊退款申请后。</p>
 */
@Getter
@Setter
public class OutpatientRefundRequestedEvent extends DomainEvent {

    /** 关联原单标识。 */
    private Long relationId;

    /** 就诊标识。 */
    private Long encounterId;

    /** 退费金额。 */
    private BigDecimal displayAmount;

    /**
     * 无参构造（反序列化用）。
     */
    public OutpatientRefundRequestedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId   支付流水标识
     * @param relationId    关联原单标识
     * @param encounterId   就诊标识
     * @param displayAmount 退费金额
     */
    public OutpatientRefundRequestedEvent(Long aggregateId, Long relationId,
                                          Long encounterId, BigDecimal displayAmount) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.relationId = relationId;
        this.encounterId = encounterId;
        this.displayAmount = displayAmount;
    }
}
