/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.settlement;

import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn.InsurancePaymentSettledExtensionIn;

/**
 * 收银落账完成 → 第三方医保事后协同扩展点。
 *
 * <p><b>调用方</b>：{@code PaymentReconciliationSettleOrchestration}、
 * {@code InsurancePaymentSettlementAfterCommitOrchestration}（health-infrastructure）。</p>
 * <p><b>吉林实现</b>：{@code InsurancePaymentSettlementExtensionImpl}（module-chs-jilin-bridge）。</p>
 */
public interface InsurancePaymentSettlementExtension {

    default boolean isEnabled() {
        return false;
    }

    /** 门诊/住院收银 SUCCESS 落账后回调（afterCommit）。 */
    default void onPaymentSettled(InsurancePaymentSettledExtensionIn command) {
    }
}
