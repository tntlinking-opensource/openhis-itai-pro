/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.events.financial.paymentreconciliation;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 支付状态编辑事件。 */
@Getter
@Setter
public class PaymentStatusEditedEvent extends DomainEvent {

    private PaymentStatus beforeStatusEnum;
    private PaymentStatus afterStatusEnum;
    private List<PaymentYbSettlementEventItem> ybSettlements;

    public PaymentStatusEditedEvent() {
        super();
    }

    public PaymentStatusEditedEvent(
        Long aggregateId,
        PaymentStatus beforeStatusEnum,
        PaymentStatus afterStatusEnum) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.beforeStatusEnum = beforeStatusEnum;
        this.afterStatusEnum = afterStatusEnum;
    }
}
