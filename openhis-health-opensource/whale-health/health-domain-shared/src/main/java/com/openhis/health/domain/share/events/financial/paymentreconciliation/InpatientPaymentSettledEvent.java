/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.events.financial.paymentreconciliation;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 住院支付已结算事件。 */
@Getter
@Setter
public class InpatientPaymentSettledEvent extends DomainEvent {

    private Long encounterId;
    private String chargeItemIds;
    private BigDecimal displayAmount;
    private List<PaymentYbSettlementEventItem> ybSettlements;
    private Long entererId;
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
