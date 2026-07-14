/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.einvoice.boss.domain.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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
}
