/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.internal.issue;

import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.enums.TaskStatus;
import com.zxplat.plugin.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.zxplat.plugin.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.zxplat.plugin.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import com.zxplat.plugin.einvoice.boss.domain.issue.service.BossIssueRetryPolicy;
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
@ConditionalOnProperty(name = "zxplat.modules.einvoice-boss.enabled", havingValue = "true", matchIfMissing = true)
public class BossIssueScheduler {

    private final EinvBossIssueTaskRepository taskRepository;
    private final BossIssueWorkerService issueWorker;
    private final EinvoiceBossIssueService issueAppService;
    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final BossIssueTaskStatusWriter taskStatusWriter;

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
                    // 其它节点已抢占，本节点不必告警
                    log.debug("Boss issue task skipped (concurrent), taskId={}", task.getId());
                } else if (isStaleStatusSkip(ex)) {
                    // 唤醒已推进到 PROCESSING/SUCCESS，调度滞后命中
                    log.debug("Boss issue task skipped (stale status), taskId={}, msg={}",
                            task.getId(), ex.getMessage());
                } else {
                    log.warn("Boss issue task failed, taskId={}, msg={}", task.getId(), ex.getMessage());
                }
            }
        }
        for (EinvBossIssueTask task : failed) {
            if (!BossIssueRetryPolicy.shouldAutoRetry(task.getLastErrorCode(), task.getLastErrorMsg())) {
                // 永久拒收：清空 nextRetryAt，避免每轮仍被扫出再打博思
                taskStatusWriter.clearAutoRetry(task.getId());
                continue;
            }
            try {
                issueWorker.processTask(task.getId());
            } catch (Exception ex) {
                if (isConcurrentSkip(ex)) {
                    // 其它节点已抢占，本节点不必告警
                    log.debug("Boss issue retry skipped (concurrent), taskId={}", task.getId());
                } else if (isStaleStatusSkip(ex)) {
                    // 唤醒已推进到 PROCESSING/SUCCESS，调度滞后命中
                    log.debug("Boss issue retry skipped (stale status), taskId={}, msg={}",
                            task.getId(), ex.getMessage());
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
        if (ex instanceof com.zxplat.framework.core.application.exception.BusinessException businessException) {
            // 抢占失败不是开票失败，调用方降为 debug
            return EinvBossErrorCodes.EINV_TASK_CONCURRENT.equals(businessException.getErrorCode());
        }
        return false;
    }

    /**
     * 唤醒与调度竞态：任务已是 PROCESSING/SUCCESS，再消费只会误报 WARN。
     *
     * @param ex Worker 抛出的异常
     * @return 状态已不可消费且属竞态时为 true
     */
    private boolean isStaleStatusSkip(Exception ex) {
        if (!(ex instanceof com.zxplat.framework.core.application.exception.BusinessException businessException)) {
            return false;
        }
        // 仅「任务状态不可消费」与唤醒抢占重叠，其它 NOT_FOUND 仍应告警
        if (!EinvBossErrorCodes.EINV_TASK_NOT_FOUND.equals(businessException.getErrorCode())) {
            return false;
        }
        String message = businessException.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains(TaskStatus.PROCESSING.getCode())
                || message.contains(TaskStatus.SUCCESS.getCode());
    }
}
