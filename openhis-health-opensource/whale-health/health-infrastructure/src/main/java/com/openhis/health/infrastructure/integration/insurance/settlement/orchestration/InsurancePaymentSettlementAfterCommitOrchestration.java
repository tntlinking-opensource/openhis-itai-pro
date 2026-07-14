/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.settlement.orchestration;

import com.openhis.health.infrastructure.integration.insurance.settlement.InsurancePaymentSettlementExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn.InsurancePaymentSettledExtensionIn;
import com.openhis.health.infrastructure.integration.noop.IntegrationAfterCommitSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 收银落账后 afterCommit 触发 {@link InsurancePaymentSettlementExtension#onPaymentSettled}。 */
@Component
@RequiredArgsConstructor
public class InsurancePaymentSettlementAfterCommitOrchestration {

    private final InsurancePaymentSettlementExtension insurancePaymentSettlementExtension;

    public void afterPaymentSettled(InsurancePaymentSettledExtensionIn command) {
        if (command == null || command.getPaymentId() == null || !insurancePaymentSettlementExtension.isEnabled()) {
            return;
        }
        IntegrationAfterCommitSupport.runAfterCommit(
                () -> insurancePaymentSettlementExtension.onPaymentSettled(command));
    }
}
