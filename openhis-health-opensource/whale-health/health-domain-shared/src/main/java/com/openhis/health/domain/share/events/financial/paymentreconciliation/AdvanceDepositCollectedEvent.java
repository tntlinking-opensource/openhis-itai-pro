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
 * 预交金已收取事件。
 *
 * <p>触发时机：住院预交金收取落 DRAFT 后（{@code settleInpatientPayment} 收口前）。</p>
 */
@Getter
@Setter
public class AdvanceDepositCollectedEvent extends DomainEvent {

    /** 就诊标识。 */
    private Long encounterId;

    /**
     * 患者标识（DOC-CONFLICT：DM §5.1 要求「账户标识」，当前实体未存储独立 accountId，以 patientId 替代；
     * 如后续补充 accountId 字段，请将本字段替换并修正事件构造调用）。
     */
    private Long patientId;

    /** 收取金额。 */
    private BigDecimal displayAmount;
    /**
     * 收费员标识（用于日结分组）。
     * 向后兼容：可通过 setter 注入。
     */
    private Long entererId;
    /**
     * 收取时间（用于日结日期归属）。
     * 向后兼容：可通过 setter 注入。
     */
    private java.time.LocalDateTime collectionTime;

    /** 无参构造（反序列化用）。 */
    public AdvanceDepositCollectedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；entererId / collectionTime 通过 setter 注入。
     *
     * @param aggregateId   支付流水标识
     * @param encounterId   就诊标识
     * @param patientId     患者标识（替代账户标识，见字段说明）
     * @param displayAmount 收取金额
     */
    public AdvanceDepositCollectedEvent(Long aggregateId, Long encounterId,
                                        Long patientId, BigDecimal displayAmount) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.displayAmount = displayAmount;
    }
}
