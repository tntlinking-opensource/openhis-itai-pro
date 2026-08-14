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
import com.openhis.health.infrastructure.integration.financial.PaymentFinTypeStaticSnapshotHook;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn.InsurancePaymentSettledExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.settlement.assembler.InsurancePaymentSettledExtensionInAssembler;
import com.openhis.health.infrastructure.integration.insurance.settlement.orchestration.InsurancePaymentSettlementAfterCommitOrchestration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** 支付落账编排：domain {@code settle*Payment} + 费用分类快照 + 医保事后挂接（2601 路径 A）。 */
@Component
@RequiredArgsConstructor
public class PaymentReconciliationSettleOrchestration {

    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final InsurancePaymentSettlementAfterCommitOrchestration insurancePaymentSettlementAfterCommitOrchestration;
    /** 可选：application 层快照写入（与领域事件 Listener 双保险）。 */
    private final ObjectProvider<PaymentFinTypeStaticSnapshotHook> paymentFinTypeStaticSnapshotHook;

    public Long settleOutpatientPayment(SettleOutpatientPaymentCommand command) {
        Long paymentId = paymentReconciliationDomainService.settleOutpatientPayment(command);
        // Cmd：同事务同步写 fin_type 快照，避免仅依赖 beforeCommit 事件偶发漏写
        this.writeFinTypeStaticSnapshot(paymentId);
        notifyInsuranceAfterSettle(paymentId, command.getYbSettlements(), false);
        return paymentId;
    }

    public Long settleInpatientPayment(SettleInpatientPaymentCommand command) {
        Long paymentId = paymentReconciliationDomainService.settleInpatientPayment(command);
        // Cmd：同事务同步写 fin_type 快照（与门诊 settle 对称）
        this.writeFinTypeStaticSnapshot(paymentId);
        notifyInsuranceAfterSettle(paymentId, command.getYbSettlements(), true);
        return paymentId;
    }

    /**
     * 结算成功后写入费用分类快照；无 application 实现时跳过（单测/精简装配）。
     *
     * @param paymentId 支付主键
     */
    private void writeFinTypeStaticSnapshot(Long paymentId) {
        PaymentFinTypeStaticSnapshotHook hook = this.paymentFinTypeStaticSnapshotHook.getIfAvailable();
        if (hook == null) {
            return;
        }
        hook.writeForPayment(paymentId);
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
