/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.compensation;

import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.enums.CompensationExceptionType;
import com.openhis.whale.einvoice.boss.domain.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossCompensationClassifierTest {

    @Test
    void classify_lost_whenDraftWithoutTask() {
        BossIssueSnapshot snapshot = draftSnapshot(LocalDateTime.now().minusMinutes(5));
        Optional<CompensationExceptionType> type = BossCompensationClassifier.classify(
                snapshot, Optional.empty(), 30, LocalDateTime.now());
        assertEquals(CompensationExceptionType.LOST, type.orElseThrow());
    }

    @Test
    void classify_amount_whenFailedWithMismatch() {
        BossIssueSnapshot snapshot = draftSnapshot(LocalDateTime.now().minusMinutes(5));
        EinvBossIssueTask task = failedTask(EinvBossErrorCodes.EINV_AMOUNT_MISMATCH);
        Optional<CompensationExceptionType> type = BossCompensationClassifier.classify(
                snapshot, Optional.of(task), 30, LocalDateTime.now());
        assertEquals(CompensationExceptionType.AMOUNT, type.orElseThrow());
    }

    @Test
    void classify_stale_whenDraftTooLong() {
        BossIssueSnapshot snapshot = draftSnapshot(LocalDateTime.now().minusMinutes(45));
        EinvBossIssueTask task = pendingTask();
        Optional<CompensationExceptionType> type = BossCompensationClassifier.classify(
                snapshot, Optional.of(task), 30, LocalDateTime.now());
        assertEquals(CompensationExceptionType.STALE, type.orElseThrow());
    }

    @Test
    void classify_empty_whenIssued() {
        BossIssueSnapshot snapshot = draftSnapshot(LocalDateTime.now());
        snapshot.setInvoiceStatus(InvoiceStatus.ISSUED.getCode());
        assertTrue(BossCompensationClassifier.classify(snapshot, Optional.empty(), 30, LocalDateTime.now()).isEmpty());
    }

    private BossIssueSnapshot draftSnapshot(LocalDateTime billDateTime) {
        BossIssueSnapshot snapshot = new BossIssueSnapshot();
        snapshot.setInvoiceId(1L);
        snapshot.setInvoiceStatus(InvoiceStatus.DRAFT.getCode());
        snapshot.setBillDateTime(billDateTime);
        return snapshot;
    }

    private EinvBossIssueTask failedTask(String errorCode) {
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setStatus(TaskStatus.FAILED.getCode());
        task.setLastErrorCode(errorCode);
        return task;
    }

    private EinvBossIssueTask pendingTask() {
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setStatus(TaskStatus.PENDING.getCode());
        return task;
    }
}
