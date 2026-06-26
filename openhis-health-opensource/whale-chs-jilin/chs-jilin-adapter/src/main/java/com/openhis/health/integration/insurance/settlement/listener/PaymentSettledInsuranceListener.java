/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement.listener;

import com.openhis.health.domain.share.events.financial.paymentreconciliation.InpatientPaymentSettledEvent;
import com.openhis.health.domain.share.events.financial.paymentreconciliation.OutpatientPaymentSettledEvent;
import com.openhis.health.domain.share.events.financial.paymentreconciliation.PaymentYbSettlementEventItem;
import com.openhis.whale.chs.jilin.contracts.client.ChsPaymentSettlementClient;
import com.openhis.whale.chs.jilin.contracts.dto.PaymentSettlementTrigger;
import com.openhis.whale.chs.jilin.contracts.dto.PaymentYbSettlementTriggerItem;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

/** 收银结算事件 → {@link ChsPaymentSettlementClient}。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class PaymentSettledInsuranceListener {

    private final ChsPaymentSettlementClient chsPaymentSettlementClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutpatientPaymentSettled(OutpatientPaymentSettledEvent event) {
        chsPaymentSettlementClient.onPaymentSettled(toTrigger(event, false));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInpatientPaymentSettled(InpatientPaymentSettledEvent event) {
        chsPaymentSettlementClient.onPaymentSettled(toTrigger(event, true));
    }

    private static PaymentSettlementTrigger toTrigger(OutpatientPaymentSettledEvent event, boolean inpatient) {
        PaymentSettlementTrigger trigger = new PaymentSettlementTrigger();
        trigger.setPaymentId(event.getAggregateId());
        trigger.setEncounterId(event.getEncounterId());
        trigger.setChargeItemIds(event.getChargeItemIds());
        trigger.setDisplayAmount(event.getDisplayAmount());
        trigger.setBillDate(event.getBillDate());
        trigger.setEntererId(event.getEntererId());
        trigger.setYbSettlements(toTriggerItems(event.getYbSettlements()));
        trigger.setInpatient(inpatient);
        return trigger;
    }

    private static PaymentSettlementTrigger toTrigger(InpatientPaymentSettledEvent event, boolean inpatient) {
        PaymentSettlementTrigger trigger = new PaymentSettlementTrigger();
        trigger.setPaymentId(event.getAggregateId());
        trigger.setEncounterId(event.getEncounterId());
        trigger.setChargeItemIds(event.getChargeItemIds());
        trigger.setDisplayAmount(event.getDisplayAmount());
        trigger.setBillDate(event.getBillDate());
        trigger.setEntererId(event.getEntererId());
        trigger.setYbSettlements(toTriggerItems(event.getYbSettlements()));
        trigger.setInpatient(inpatient);
        return trigger;
    }

    private static List<PaymentYbSettlementTriggerItem> toTriggerItems(List<PaymentYbSettlementEventItem> items) {
        List<PaymentYbSettlementTriggerItem> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (PaymentYbSettlementEventItem item : items) {
            if (item == null) {
                continue;
            }
            PaymentYbSettlementTriggerItem row = new PaymentYbSettlementTriggerItem();
            row.setSetlId(item.getSetlId());
            row.setChrgBchno(item.getChrgBchno());
            row.setMdtrtId(item.getMdtrtId());
            row.setSettlementRecordId(item.getSettlementRecordId());
            row.setAccountId(item.getAccountId());
            row.setStatus(item.getStatus());
            result.add(row);
        }
        return result;
    }
}
