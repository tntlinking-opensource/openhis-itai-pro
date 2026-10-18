/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.financial.paymentreconciliation;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 住院支付已结算事件。 */
@Getter
@Setter
public class InpatientPaymentSettledEvent extends DomainEvent {

    /** 就诊主键（InpatientPaymentSettledEvent）。 */
    private Long encounterId;
    /** charge明细项Ids（InpatientPaymentSettledEvent）。 */
    private String chargeItemIds;
    /** displayAmount（InpatientPaymentSettledEvent）。 */
    private BigDecimal displayAmount;
    /** ybSettlements（InpatientPaymentSettledEvent）。 */
    private List<PaymentYbSettlementEventItem> ybSettlements;
    /** enterer主键（InpatientPaymentSettledEvent）。 */
    private Long entererId;
    /** billDate（InpatientPaymentSettledEvent）。 */
    private LocalDateTime billDate;

    public InpatientPaymentSettledEvent() {
        super();
    }

    public InpatientPaymentSettledEvent(
        Long aggregateId,
        Long encounterId,
        String chargeItemIds,
        BigDecimal displayAmount) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.encounterId = encounterId;
        this.chargeItemIds = chargeItemIds;
        this.displayAmount = displayAmount;
    }
}
