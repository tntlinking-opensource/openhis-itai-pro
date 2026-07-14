/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.workinjury;

import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryConfirmSettleExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryPreSettleQueryExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryReverseExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjurySettleExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryConfirmSettleExtensionOut;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryPreSettleQueryExtensionOut;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjurySettlementRecordExtensionOut;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 工伤门诊收银扩展点（预结算 invoke、落账确认、退费）。
 */
public interface WorkInjuryOutpatientSettlementExtension {

    default boolean supportsWorkInjuryAccount(Long contractId) {
        return false;
    }

    default void assertQualificationBeforePreSettle(Long encounterId) {
    }

    default PrepareWorkInjuryInvokeExtensionOut startSettleSession(WorkInjurySettleExtensionIn command) {
        throw new UnsupportedOperationException("工伤门诊预结算未装配");
    }

    default WorkInjuryConfirmSettleExtensionOut confirmSettle(WorkInjuryConfirmSettleExtensionIn command) {
        throw new UnsupportedOperationException("工伤门诊落账确认未装配");
    }

    default Optional<WorkInjuryPreSettleQueryExtensionOut> findActivePreSettleByPaymentDraft(
            WorkInjuryPreSettleQueryExtensionIn query) {
        return Optional.empty();
    }

    default PrepareWorkInjuryInvokeExtensionOut startReverseSession(WorkInjuryReverseExtensionIn command) {
        throw new UnsupportedOperationException("工伤门诊退费未装配");
    }

    default List<WorkInjurySettlementRecordExtensionOut> listSettlementsByPayment(Long paymentId) {
        return Collections.emptyList();
    }
}
