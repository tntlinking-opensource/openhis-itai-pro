/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.settlement.assembler;

import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentYbSettlementItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn.InsurancePaymentSettledExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn.InsurancePaymentYbSettlementLineExtensionIn;

import java.util.ArrayList;
import java.util.List;

/** {@link InsurancePaymentSettledExtensionIn} 纯拼装（domain → SPI）。 */
public final class InsurancePaymentSettledExtensionInAssembler {

    private InsurancePaymentSettledExtensionInAssembler() {
    }

    public static InsurancePaymentSettledExtensionIn fromPaymentSettled(
            PaymentReconciliation payment,
            Long paymentId,
            List<PaymentYbSettlementItemCommand> ybItems,
            boolean inpatient) {
        if (payment == null || paymentId == null) {
            return null;
        }
        InsurancePaymentSettledExtensionIn settledCommand = new InsurancePaymentSettledExtensionIn();
        settledCommand.setPaymentId(paymentId);
        settledCommand.setEncounterId(payment.getEncounterId());
        settledCommand.setChargeItemIds(payment.getChargeItemIds());
        settledCommand.setDisplayAmount(payment.getDisplayAmount());
        settledCommand.setBillDate(payment.getBillDate());
        settledCommand.setEntererId(payment.getEntererId());
        settledCommand.setInpatient(inpatient);
        settledCommand.setYbSettlements(toYbLines(ybItems));
        return settledCommand;
    }

    public static List<InsurancePaymentYbSettlementLineExtensionIn> toYbLines(
            List<PaymentYbSettlementItemCommand> items) {
        List<InsurancePaymentYbSettlementLineExtensionIn> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (PaymentYbSettlementItemCommand item : items) {
            if (item == null) {
                continue;
            }
            InsurancePaymentYbSettlementLineExtensionIn row = new InsurancePaymentYbSettlementLineExtensionIn();
            row.setSettlementId(item.getSetlId());
            row.setChargeBatchNo(item.getChrgBchno());
            row.setEncounterPlatformId(item.getMdtrtId());
            row.setSettlementRecordId(item.getSettlementRecordId());
            row.setAccountId(item.getAccountId());
            row.setStatus(item.getStatus());
            result.add(row);
        }
        return result;
    }
}
