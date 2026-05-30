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
 * 住院支付已结算事件。
 *
 * <p>触发时机：住院结算成功后。</p>
 */
@Getter
@Setter
public class InpatientPaymentSettledEvent extends DomainEvent {

    /** 就诊标识。 */
    private Long encounterId;

    /** 收费明细标识集合（JSON 数组字符串）。 */
    private String chargeItemIds;

    /** 应收金额。 */
    private BigDecimal displayAmount;

    /** 医保结算标识集合（JSON 数组字符串）。 */
    private String ybSettleIds;
    /**
     * 收费员标识（用于日结汇总分组）。
     * 向后兼容：可通过 setter 注入。
     */
    private Long entererId;
    /**
     * 结算时间（用于日结汇总日期归属）。
     * 向后兼容：可通过 setter 注入。
     */
    private java.time.LocalDateTime billDate;

    /** 无参构造（反序列化用）。 */
    public InpatientPaymentSettledEvent() {
        super();
    }

    /**
     * 向后兼容构造器；entererId / billDate 通过 setter 注入。
     *
     * @param aggregateId   支付流水标识
     * @param encounterId   就诊标识
     * @param chargeItemIds 收费明细标识集合
     * @param displayAmount 应收金额
     * @param ybSettleIds   医保结算标识集合
     */
    public InpatientPaymentSettledEvent(Long aggregateId, Long encounterId, String chargeItemIds,
                                        BigDecimal displayAmount, String ybSettleIds) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.encounterId = encounterId;
        this.chargeItemIds = chargeItemIds;
        this.displayAmount = displayAmount;
        this.ybSettleIds = ybSettleIds;
    }
}
