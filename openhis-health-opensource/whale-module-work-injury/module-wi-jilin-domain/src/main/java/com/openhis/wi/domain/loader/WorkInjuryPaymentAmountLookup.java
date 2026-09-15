/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.loader;

import com.openhis.wi.domain.recon.result.WorkInjuryPaymentAmountSnapshot;

import java.util.Collection;
import java.util.Map;

/**
 * 按 paymentId 批量汇总收银明细金额（bridge 实现）。
 */
public interface WorkInjuryPaymentAmountLookup {

    /**
     * @param paymentIds fin_payment_reconciliation.id 集合
     * @return paymentId → 金额快照；缺失 id 不入 map
     */
    Map<Long, WorkInjuryPaymentAmountSnapshot> findByPaymentIds(Collection<Long> paymentIds);
}
