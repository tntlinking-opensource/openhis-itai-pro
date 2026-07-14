/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
