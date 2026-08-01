/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.domain.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开票任务定时兜底：消费滞留 PENDING / 可重试 FAILED + 扫描 orphan DRAFT。
 *
 * <p>主路径由 {@link BossIssueWorkerWakeBridge} 在入队后即时唤醒；本调度仅作
 * 进程崩溃、唤醒失败、退避重试的补偿。多实例下并发抢占失败
 *（{@link EinvBossErrorCodes#EINV_TASK_CONCURRENT}）记 debug，不视为故障。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.einvoice-boss.enabled", havingValue = "true")
public class BossIssueScheduler {

    private final EinvBossIssueTaskRepository taskRepository;
    private final BossIssueWorkerService issueWorker;
    private final EinvoiceBossIssueService issueAppService;
    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;

    /**
     * 低频扫表兜底：滞留 PENDING + 到达重试点的 FAILED。
     *
     * <p>默认 2 分钟；主开票延迟由入队唤醒承担，不必再 30 秒空转。</p>
     */
    @Scheduled(fixedDelayString = "${einvoice.boss.scheduler.poll-ms:120000}")
    public void pollRetryableTasks() {
        List<EinvBossIssueTask> pending = taskRepository.findByStatusAndIsDeletedFalse(TaskStatus.PENDING.getCode());
        List<EinvBossIssueTask> failed = taskRepository.findByStatusAndIsDeletedFalseAndNextRetryAtBefore(
                TaskStatus.FAILED.getCode(), LocalDateTime.now());
        for (EinvBossIssueTask task : pending) {
            try {
                issueWorker.processTask(task.getId());
            } catch (Exception ex) {
                if (isConcurrentSkip(ex)) {
                    log.debug("Boss issue task skipped (concurrent), taskId={}", task.getId());
                } else {
                    log.warn("Boss issue task failed, taskId={}, msg={}", task.getId(), ex.getMessage());
                }
            }
        }
        for (EinvBossIssueTask task : failed) {
            try {
                issueWorker.processTask(task.getId());
            } catch (Exception ex) {
                if (isConcurrentSkip(ex)) {
                    log.debug("Boss issue retry skipped (concurrent), taskId={}", task.getId());
                } else {
                    log.warn("Boss issue retry failed, taskId={}, msg={}", task.getId(), ex.getMessage());
                }
            }
        }
    }

    /**
     * 每 5 分钟扫描 orphan DRAFT（事件丢失兜底）；入队后会即时唤醒。
     */
    @Scheduled(fixedDelayString = "${einvoice.boss.scheduler.orphan-scan-ms:300000}")
    public void scanOrphanDrafts() {
        List<Long> invoiceIds = issueSnapshotContextLoader.findOrphanDraftInvoiceIds(20);
        for (Long invoiceId : invoiceIds) {
            issueAppService.scheduleIssueAsync(invoiceId);
        }
    }

    /**
     * 是否为另一节点已抢占导致的可忽略异常。
     *
     * @param ex Worker 抛出的异常
     * @return 错误码为 EINV_TASK_CONCURRENT 时为 true
     */
    private boolean isConcurrentSkip(Exception ex) {
        if (ex instanceof com.openhis.whale.core.application.exception.BusinessException businessException) {
            return EinvBossErrorCodes.EINV_TASK_CONCURRENT.equals(businessException.getErrorCode());
        }
        return false;
    }
}
