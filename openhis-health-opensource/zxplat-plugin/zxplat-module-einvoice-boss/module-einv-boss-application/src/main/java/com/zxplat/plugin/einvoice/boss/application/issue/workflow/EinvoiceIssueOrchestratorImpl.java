/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.issue.workflow;

import com.zxplat.plugin.einvoice.boss.application.internal.issue.BossRecoverService;
import com.zxplat.plugin.einvoice.boss.application.internal.issue.BossWriteOffService;
import com.zxplat.plugin.einvoice.boss.application.internal.issue.EinvoiceBossIssueService;
import com.zxplat.plugin.einvoice.boss.domain.issue.access.EinvoiceIssueOrchestrator;
import com.zxplat.plugin.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link EinvoiceIssueOrchestrator} �?application 层实现（domain 端口 �?Worker / 同步服务）�?
 *
 * <p>�?CHS bridge Orchestrator 不同：Boss 异步 Worker、任务表、Flyway 均在 application�?
 * 故本类驻�?{@code application/issue/workflow/} 而非 bridge。bridge Extension 仅依�?domain 接口�?/p>
 */
@Component
@Primary
@RequiredArgsConstructor
public class EinvoiceIssueOrchestratorImpl implements EinvoiceIssueOrchestrator {

    private final EinvoiceBossIssueService issueAppService;
    private final BossRecoverService recoverService;
    private final BossWriteOffService writeOffService;
    private final BossElectronicInvoiceAsyncBridge asyncBridge;
    private final EinvBossDocumentRepository documentRepository;

    /** {@inheritDoc} */
    @Override
    public void scheduleIssueAsync(Long invoiceId) {
        issueAppService.scheduleIssueAsync(invoiceId);
    }

    /** {@inheritDoc} */
    @Override
    public BossInvoiceIssueResult issueByDocument(Long documentId) {
        return issueAppService.issueByDocument(documentId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现：查�?{@code einv_boss_document.boss_bill_no} 是否非空�?/p>
     */
    @Override
    public boolean canWriteOff(Long invoiceId) {
        if (invoiceId == null) {
            return false;
        }
        return documentRepository.findFirstByInvoiceIdAndIsDeletedFalseOrderByIdAsc(invoiceId)
                .map(doc -> StringUtils.isNotBlank(doc.getBossBillNo()))
                .orElse(false);
    }

    /** {@inheritDoc} */
    @Override
    public void scheduleWriteOffAsync(Long invoiceId, String reason) {
        asyncBridge.scheduleWriteOffAsync(invoiceId, reason);
    }

    /** {@inheritDoc} */
    @Override
    public BossInvoiceIssueResult writeOff(Long invoiceId, String reason) {
        return writeOffService.writeOff(invoiceId, reason);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<BossInvoiceIssueResult> recoverByBusNo(String busNo) {
        return recoverService.recoverByBusNo(busNo, null);
    }
}
