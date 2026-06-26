/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support.einvoice;

import com.openhis.health.integration.einvoice.BossElectronicInvoiceExtension;
import com.openhis.health.integration.support.IntegrationAfterCommitSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 票据草稿/作废后触发博思电子票据 Extension（afterCommit）。 */
@Component
@RequiredArgsConstructor
public class BossElectronicInvoiceIntegrationSupport {

    private final BossElectronicInvoiceExtension bossElectronicInvoiceExtension;

    public void afterDraftCreated(Long invoiceId) {
        if (invoiceId == null || !bossElectronicInvoiceExtension.isEnabled()) {
            return;
        }
        IntegrationAfterCommitSupport.runAfterCommit(
                () -> bossElectronicInvoiceExtension.scheduleIssueAfterDraft(invoiceId));
    }

    public void afterInvoiceCancelled(Long invoiceId, String reason) {
        if (invoiceId == null || !bossElectronicInvoiceExtension.isEnabled()) {
            return;
        }
        IntegrationAfterCommitSupport.runAfterCommit(
                () -> bossElectronicInvoiceExtension.scheduleWriteOffAfterCancel(invoiceId, reason));
    }
}
