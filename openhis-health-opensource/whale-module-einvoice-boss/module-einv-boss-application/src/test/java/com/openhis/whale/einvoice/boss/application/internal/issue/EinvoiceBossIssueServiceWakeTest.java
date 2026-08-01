/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.einvoice.boss.application.internal.document.EinvoiceBossDocumentInitializerService;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.enums.TaskTriggerSource;
import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import com.openhis.whale.einvoice.boss.domain.writeoff.loader.EinvoiceInvoiceWritebackLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EinvoiceBossIssueServiceWakeTest {

    @Mock
    private BossIssueTaskEnqueueService enqueueService;
    @Mock
    private EinvBossIssueTaskRepository taskRepository;
    @Mock
    private EinvoiceBossDocumentInitializerService documentInitializer;
    @Mock
    private BossIssueWorkerService issueWorker;
    @Mock
    private BossIssueWorkerWakeBridge wakeBridge;
    @Mock
    private BossRecoverService recoverService;
    @Mock
    private BossWriteOffService writeOffService;
    @Mock
    private EinvoiceInvoiceWritebackLoader invoiceWritebackLoader;
    @Mock
    private EinvBossDocumentRepository documentRepository;

    @InjectMocks
    private EinvoiceBossIssueService issueService;

    @Test
    void scheduleIssueAsync_enqueuesAndWakes() {
        when(taskRepository.findByInvoiceIdAndIsDeletedFalse(10L)).thenReturn(Optional.empty());
        when(enqueueService.enqueue(eq(10L), eq(TaskTriggerSource.EVENT.getCode())))
                .thenReturn(Optional.of(99L));

        issueService.scheduleIssueAsync(10L);

        verify(wakeBridge).wakeProcessTaskAfterCommit(99L);
    }

    @Test
    void scheduleIssueAsync_existingPending_wakes() {
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setId(7L);
        task.setStatus(TaskStatus.PENDING.getCode());
        when(taskRepository.findByInvoiceIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(task));

        issueService.scheduleIssueAsync(10L);

        verify(enqueueService, never()).enqueue(any(), any());
        verify(wakeBridge).wakeProcessTaskAfterCommit(7L);
    }

    @Test
    void scheduleIssueAsync_existingSuccess_doesNotWake() {
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setId(7L);
        task.setStatus(TaskStatus.SUCCESS.getCode());
        when(taskRepository.findByInvoiceIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(task));

        issueService.scheduleIssueAsync(10L);

        verify(wakeBridge, never()).wakeProcessTaskAfterCommit(any());
    }

    @Test
    void requeue_resetsAndWakes() {
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setId(5L);
        task.setStatus(TaskStatus.FAILED.getCode());
        when(taskRepository.findByInvoiceIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(task));

        issueService.requeue(10L);

        verify(taskRepository).save(task);
        verify(wakeBridge).wakeProcessTaskAfterCommit(5L);
    }
}
