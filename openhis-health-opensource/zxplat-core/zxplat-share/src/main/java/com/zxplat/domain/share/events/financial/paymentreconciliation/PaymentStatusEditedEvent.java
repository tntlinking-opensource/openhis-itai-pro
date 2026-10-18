/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.financial.paymentreconciliation;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 支付状态编辑事件。 */
@Getter
@Setter
public class PaymentStatusEditedEvent extends DomainEvent {

    /** before状态编码（PaymentStatusEditedEvent）。 */
    private PaymentStatus beforeStatusEnum;
    /** after状态编码（PaymentStatusEditedEvent）。 */
    private PaymentStatus afterStatusEnum;
    /** ybSettlements（PaymentStatusEditedEvent）。 */
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
