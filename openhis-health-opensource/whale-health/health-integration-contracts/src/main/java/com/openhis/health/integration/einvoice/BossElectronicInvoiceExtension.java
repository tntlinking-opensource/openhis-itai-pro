/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.einvoice;

/**
 * 博思电子票据 Extension（草稿开票 / 冲红）。
 *
 * <p><b>实现</b>：{@code BossElectronicInvoiceExtensionImpl}（einvoice-boss-adapter）。</p>
 */
public interface BossElectronicInvoiceExtension {

    default boolean isEnabled() {
        return false;
    }

    default void scheduleIssueAfterDraft(Long invoiceId) {
        // NoOp
    }

    default void scheduleWriteOffAfterCancel(Long invoiceId, String reason) {
        // NoOp
    }
}
