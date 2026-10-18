/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.financial;

/**
 * 支付落账后写入费用分类快照的钩子（由 application 层实现，infrastructure 编排侧可选注入）。
 *
 * <p>与领域事件 Listener 双保险：同事务内同步写一次，避免仅依赖 beforeCommit 事件时偶发漏写。</p>
 */
public interface PaymentFinTypeStaticSnapshotHook {

    /**
     * 按支付主键重建 {@code fin_payment_rec_static}（预交金等无明细流水由实现方跳过）。
     *
     * @param paymentId 支付主键
     */
    void writeForPayment(Long paymentId);
}
