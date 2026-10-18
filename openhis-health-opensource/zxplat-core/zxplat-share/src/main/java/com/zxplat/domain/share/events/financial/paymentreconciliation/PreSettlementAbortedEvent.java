/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.financial.paymentreconciliation;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 预结算已放弃事件。
 *
 * <p>触发时机：收费前放弃预结算后。</p>
 */
@Getter
@Setter
public class PreSettlementAbortedEvent extends DomainEvent {

    /** 就诊标识。 */
    private Long encounterId;

    /**
     * 无参构造（反序列化用）。
     */
    public PreSettlementAbortedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId 支付流水标识
     * @param encounterId 就诊标识
     */
    public PreSettlementAbortedEvent(Long aggregateId, Long encounterId) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.encounterId = encounterId;
    }
}
