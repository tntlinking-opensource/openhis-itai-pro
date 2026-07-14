/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.einvoice;

/**
 * 博思电子票据 Extension（草稿开票 / 冲红）。
 *
 * <p><b>实现</b>：{@code BossElectronicInvoiceExtensionImpl}（einvoice-boss-adapter）。</p>
 */
public interface BossElectronicInvoiceExtension {

    /**
     * 博思电子票据扩展是否已装配。
     *
     * @return false 时发票草稿不触发开票/冲红
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
}
