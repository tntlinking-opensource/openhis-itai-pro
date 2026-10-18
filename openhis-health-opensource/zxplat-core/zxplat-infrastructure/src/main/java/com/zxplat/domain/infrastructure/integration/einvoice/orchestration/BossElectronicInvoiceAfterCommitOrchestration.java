/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.einvoice.orchestration;

import com.zxplat.domain.infrastructure.integration.einvoice.BossElectronicInvoiceExtension;
import com.zxplat.domain.infrastructure.integration.noop.IntegrationAfterCommitSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 博思电子票据：草稿/作废落库后 afterCommit 触发 {@link BossElectronicInvoiceExtension}。 */
@Component
@RequiredArgsConstructor
public class BossElectronicInvoiceAfterCommitOrchestration {

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
