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
import java.time.LocalDateTime;

/**
 * 预交金已退还事件。
 *
 * <p>触发时机：住院预交金退还落 DRAFT 后（结算收口前）。</p>
 */
@Getter
@Setter
public class AdvanceDepositRefundedEvent extends DomainEvent {

    /** 就诊标识。 */
    private Long encounterId;

    /**
     * 患者标识（与 {@link AdvanceDepositCollectedEvent} 一致；账户余额由应用层在结算后更新）。
     */
    private Long patientId;

    /** 退还金额（正数）。 */
    private BigDecimal displayAmount;

    /** 收费员标识（用于日结分组）。 */
    private Long entererId;

    /** 退还时间（用于日结日期归属）。 */
    private LocalDateTime refundTime;

    /** 无参构造（反序列化用）。 */
    public AdvanceDepositRefundedEvent() {
        super();
    }

    /**
     * 构造预交金退还事件；经办人与退还时刻可通过 setter 注入。
     *
     * @param aggregateId   支付流水标识
     * @param encounterId   就诊标识
     * @param patientId     患者标识
     * @param displayAmount 退还金额
     */
    public AdvanceDepositRefundedEvent(Long aggregateId, Long encounterId,
                                       Long patientId, BigDecimal displayAmount) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.displayAmount = displayAmount;
    }
}
