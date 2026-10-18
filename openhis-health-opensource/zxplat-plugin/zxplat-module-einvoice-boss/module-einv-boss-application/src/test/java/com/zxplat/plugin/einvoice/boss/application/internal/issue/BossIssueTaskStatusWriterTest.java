/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.internal.issue;

import com.zxplat.plugin.einvoice.boss.domain.enums.TaskStatus;
import com.zxplat.plugin.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.zxplat.plugin.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.share.constants.EinvBossPlatformCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BossIssueTaskStatusWriterTest {

    @Mock
    private EinvBossIssueTaskRepository taskRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @InjectMocks
    private BossIssueTaskStatusWriter taskStatusWriter;

    @Test
    void tryClaimProcessing_returnsTrueWhenUpdated() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(taskRepository.claimForProcessing(eq(1L), eq(TaskStatus.PROCESSING.getCode()), anyCollection(), any()))
                .thenReturn(1);
        assertTrue(taskStatusWriter.tryClaimProcessing(1L));
    }

    @Test
    void tryClaimProcessing_returnsFalseWhenNotUpdated() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(taskRepository.claimForProcessing(eq(2L), eq(TaskStatus.PROCESSING.getCode()), anyCollection(), any()))
                .thenReturn(0);
        assertFalse(taskStatusWriter.tryClaimProcessing(2L));
    }

    @Test
    void failTask_clearsNextRetryAtForAuditCycleReject() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setId(9L);
        task.setAttemptCount(8);
        task.setNextRetryAt(LocalDateTime.now().plusMinutes(10));
        when(taskRepository.findById(9L)).thenReturn(Optional.of(task));

        String msg = "博思返回: " + EinvBossPlatformCodes.EYD_COM_0303 + ":请先上报数据，才能继续开票";
        taskStatusWriter.failTask(9L, EinvBossErrorCodes.EINV_BOSS_REJECT, msg);

        ArgumentCaptor<EinvBossIssueTask> captor = ArgumentCaptor.forClass(EinvBossIssueTask.class);
        verify(taskRepository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getNextRetryAt());
        assertEquals(TaskStatus.FAILED.getCode(), captor.getValue().getStatus());
    }

    @Test
    void failTask_setsBackoffForTimeout() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setId(10L);
        task.setAttemptCount(3);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        taskStatusWriter.failTask(10L, EinvBossErrorCodes.EINV_BOSS_TIMEOUT, "read timed out");

        ArgumentCaptor<EinvBossIssueTask> captor = ArgumentCaptor.forClass(EinvBossIssueTask.class);
        verify(taskRepository).saveAndFlush(captor.capture());
        assertNotNull(captor.getValue().getNextRetryAt());
    }
}
