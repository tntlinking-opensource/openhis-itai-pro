/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.internal.issue;

import com.zxplat.plugin.einvoice.boss.application.internal.common.BossCommonSnapshotSupport;
import com.zxplat.plugin.einvoice.boss.application.internal.document.EinvoiceBossDocumentInitializerService;
import com.zxplat.plugin.einvoice.boss.domain.issue.access.BossIssueDocumentResultAccess;
import com.zxplat.plugin.einvoice.boss.domain.issue.access.BossIssueOutboundAccess;
import com.zxplat.plugin.einvoice.boss.domain.enums.TaskStatus;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.zxplat.plugin.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import com.zxplat.plugin.einvoice.boss.domain.document.service.BossInvoiceAmountValidator;
import com.zxplat.plugin.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.zxplat.plugin.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.zxplat.plugin.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.zxplat.plugin.einvoice.boss.domain.writeoff.loader.EinvoiceInvoiceWritebackLoader;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BossIssueWorkerServiceTest {

    @Mock
    private EinvBossIssueTaskRepository taskRepository;
    @Mock
    private EinvBossDocumentRepository documentRepository;
    @Mock
    private EinvBossLineRepository lineRepository;
    @Mock
    private EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    @Mock
    private EinvoiceInvoiceWritebackLoader invoiceWritebackLoader;
    @Mock
    private BossInvoiceAmountValidator amountValidator;
    @Mock
    private BossIssueOutboundAccess issueOutboundAccess;
    @Mock
    private BossIssueDocumentResultAccess documentResultAccess;
    @Mock
    private BossCommonSnapshotSupport commonSnapshotSupport;
    @Mock
    private BossIssueTaskStatusWriter taskStatusWriter;
    @Mock
    private EinvoiceBossDocumentInitializerService documentInitializer;
    @Mock
    private BossRecoverService recoverService;
    @Mock
    private TenantContext tenantContext;

    @InjectMocks
    private BossIssueWorkerService issueWorker;

    @Test
    void processTask_skipsBossInvokeWhenBillNoExists() {
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setId(1L);
        task.setDocumentId(2L);
        task.setInvoiceId(3L);
        task.setStatus(TaskStatus.PENDING.getCode());

        EinvBossDocument document = new EinvBossDocument();
        document.setId(2L);
        document.setIssueKind("REG");
        document.setBossBillNo("1001651756");
        document.setBusNo("IV-1");
        document.setTotalAmt(new BigDecimal("1.00"));

        BossInvoiceIssueResult existing = new BossInvoiceIssueResult();
        existing.setBillNo("1001651756");
        existing.setBusNo("IV-1");
        existing.setTotalAmt(new BigDecimal("1.00"));

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(document));
        when(issueSnapshotContextLoader.loadForInvoice(3L))
                .thenReturn(new com.zxplat.plugin.einvoice.boss.domain.issue.result.BossIssueSnapshot());
        when(documentResultAccess.fromDocument(document)).thenReturn(existing);

        BossInvoiceIssueResult result = issueWorker.processTask(1L);

        assertEquals("1001651756", result.getBillNo());
        verify(issueOutboundAccess, never()).invokeIssue(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(taskStatusWriter, never()).tryClaimProcessing(org.mockito.ArgumentMatchers.any());
    }
}
