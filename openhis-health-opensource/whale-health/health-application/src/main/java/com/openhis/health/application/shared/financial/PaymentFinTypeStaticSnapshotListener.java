/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.domain.share.events.financial.paymentreconciliation.InpatientPaymentSettledEvent;
import com.openhis.health.domain.share.events.financial.paymentreconciliation.InpatientSettleCancelledEvent;
import com.openhis.health.domain.share.events.financial.paymentreconciliation.OutpatientPaymentSettledEvent;
import com.openhis.health.domain.share.events.financial.paymentreconciliation.OutpatientRefundCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 门/住院支付结算与退费完成后，写入费用分类快照（在结算事务 beforeCommit 同事务内执行）。
 */
@Component
@RequiredArgsConstructor
public class PaymentFinTypeStaticSnapshotListener {

    private final PaymentFinTypeStaticSnapshotWriter paymentFinTypeStaticSnapshotWriter;

    @EventListener
    public void onOutpatientPaymentSettled(OutpatientPaymentSettledEvent event) {
        if (event == null) {
            return;
        }
        this.paymentFinTypeStaticSnapshotWriter.writeForPayment(event.getAggregateId());
    }

    @EventListener
    public void onInpatientPaymentSettled(InpatientPaymentSettledEvent event) {
        if (event == null) {
            return;
        }
        this.paymentFinTypeStaticSnapshotWriter.writeForPayment(event.getAggregateId());
    }

    @EventListener
    public void onOutpatientRefundCompleted(OutpatientRefundCompletedEvent event) {
        if (event == null) {
            return;
        }
        this.paymentFinTypeStaticSnapshotWriter.writeForPayment(event.getAggregateId());
    }

    @EventListener
    public void onInpatientSettleCancelled(InpatientSettleCancelledEvent event) {
        if (event == null) {
            return;
        }
        this.paymentFinTypeStaticSnapshotWriter.writeForPayment(event.getAggregateId());
    }
}
