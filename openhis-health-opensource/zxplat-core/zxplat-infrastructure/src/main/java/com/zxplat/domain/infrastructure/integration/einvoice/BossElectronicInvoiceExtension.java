/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.einvoice;

import com.zxplat.domain.infrastructure.integration.einvoice.ExtensionOut.BossElectronicInvoiceDocumentLookupExtensionOut;

import java.util.Optional;

/**
 * 博思电子票据 Extension（草稿开票 / 冲红 / 单据只读查询）。
 *
 * <p><b>实现</b>：{@code BossElectronicInvoiceExtensionImpl}（einvoice-boss bridge）。</p>
 */
public interface BossElectronicInvoiceExtension {

    /**
     * 博思电子票据扩展是否已装配。
     *
     * @return false 时发票草稿不触发开票/冲红；单据查询恒 empty
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 发票草稿确认后异步调度博思开票。
     *
     * @param invoiceId 发票草稿主键
     */
    default void scheduleIssueAfterDraft(Long invoiceId) {
        // NoOp
    }

    /**
     * 发票作废/红冲后调度博思冲红。
     *
     * @param invoiceId 发票主键
     * @param reason    冲红原因
     */
    default void scheduleWriteOffAfterCancel(Long invoiceId, String reason) {
        // NoOp
    }

    /**
     * 按支付单取最新博思单据摘要（门诊/住院开票回写后供 4101A 取票据代码）。
     *
     * <p>未装配 Module、无单据或尚未回写批次码时返回 empty。</p>
     *
     * @param paymentId {@code fin_payment_reconciliation.id}
     * @return 单据摘要；无有效单据时 empty
     */
    default Optional<BossElectronicInvoiceDocumentLookupExtensionOut> findDocumentByPaymentId(Long paymentId) {
        return Optional.empty();
    }
}
