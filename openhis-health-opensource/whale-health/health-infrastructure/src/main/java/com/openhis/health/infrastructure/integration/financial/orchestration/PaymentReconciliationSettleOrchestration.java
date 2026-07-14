/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.financial.orchestration;

import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentYbSettlementItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn.InsurancePaymentSettledExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.settlement.assembler.InsurancePaymentSettledExtensionInAssembler;
import com.openhis.health.infrastructure.integration.insurance.settlement.orchestration.InsurancePaymentSettlementAfterCommitOrchestration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 支付落账编排：domain {@code settle*Payment} + 医保事后挂接（2601 路径 A）。 */
@Component
@RequiredArgsConstructor
public class PaymentReconciliationSettleOrchestration {

    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final InsurancePaymentSettlementAfterCommitOrchestration insurancePaymentSettlementAfterCommitOrchestration;

    public Long settleOutpatientPayment(SettleOutpatientPaymentCommand command) {
        Long paymentId = paymentReconciliationDomainService.settleOutpatientPayment(command);
        notifyInsuranceAfterSettle(paymentId, command.getYbSettlements(), false);
        return paymentId;
    }

    public Long settleInpatientPayment(SettleInpatientPaymentCommand command) {
        Long paymentId = paymentReconciliationDomainService.settleInpatientPayment(command);
        notifyInsuranceAfterSettle(paymentId, command.getYbSettlements(), true);
        return paymentId;
    }

    private void notifyInsuranceAfterSettle(
            Long paymentId, List<PaymentYbSettlementItemCommand> ybItems, boolean inpatient) {
        PaymentReconciliation payment = paymentReconciliationQueryService.findOptionalPaymentEntityById(paymentId)
                .orElse(null);
        if (payment == null) {
            return;
        }
        InsurancePaymentSettledExtensionIn settledCommand =
                InsurancePaymentSettledExtensionInAssembler.fromPaymentSettled(payment, paymentId, ybItems, inpatient);
        if (settledCommand == null) {
            return;
        }
        insurancePaymentSettlementAfterCommitOrchestration.afterPaymentSettled(settledCommand);
    }
}
