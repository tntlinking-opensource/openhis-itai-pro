/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BossIssueWorkerWakeBridgeTest {

    @Mock
    private BossIssueWorkerService issueWorker;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void wakeAfterCommit_withoutTx_runsImmediately() {
        BossIssueWorkerWakeBridge bridge = newBridge();
        bridge.wakeProcessTaskAfterCommit(1L);
        verify(issueWorker).processTask(1L);
    }

    @Test
    void wakeAfterCommit_withTx_runsOnlyAfterCommit() {
        BossIssueWorkerWakeBridge bridge = newBridge();
        TransactionSynchronizationManager.initSynchronization();
        bridge.wakeProcessTaskAfterCommit(2L);
        verify(issueWorker, never()).processTask(2L);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(issueWorker).processTask(2L);
    }

    @Test
    void wakeProcessTask_swallowsConcurrent() {
        doThrow(new BusinessException(EinvBossErrorCodes.EINV_TASK_CONCURRENT, "抢占失败"))
                .when(issueWorker).processTask(3L);
        BossIssueWorkerWakeBridge bridge = new BossIssueWorkerWakeBridge(issueWorker, null);
        bridge.wakeProcessTask(3L);
        verify(issueWorker).processTask(3L);
    }

    /** self 指向可同步执行的代理，绕过 @Async 便于断言。 */
    private BossIssueWorkerWakeBridge newBridge() {
        BossIssueWorkerWakeBridge syncSelf = new BossIssueWorkerWakeBridge(issueWorker, null);
        return new BossIssueWorkerWakeBridge(issueWorker, syncSelf);
    }
}
