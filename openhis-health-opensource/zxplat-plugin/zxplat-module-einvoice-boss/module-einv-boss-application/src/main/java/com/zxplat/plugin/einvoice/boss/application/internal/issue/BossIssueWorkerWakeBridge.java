/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.internal.issue;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 开票任务即时唤醒：入队/重入队后触发 Worker，避免仅依赖定时扫表。
 *
 * <p>有活跃事务时等 {@code afterCommit} 再 {@code @Async} 消费，防止补偿页等 AppService
 * 事务未提交时抢占失败；无事务时直接异步。多实例仍靠
 * {@link BossIssueTaskStatusWriter#tryClaimProcessing} 防重。</p>
 */
@Slf4j
@Service
public class BossIssueWorkerWakeBridge {

    private final BossIssueWorkerService issueWorker;
    private final BossIssueWorkerWakeBridge self;

    public BossIssueWorkerWakeBridge(
            BossIssueWorkerService issueWorker,
            @Lazy BossIssueWorkerWakeBridge self) {
        this.issueWorker = issueWorker;
        this.self = self;
    }

    /**
     * 事务提交后再异步唤醒；无事务则立即异步。
     *
     * @param taskId {@code einv_boss_issue_task.id}；null 忽略
     */
    public void wakeProcessTaskAfterCommit(Long taskId) {
        if (taskId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            self.wakeProcessTask(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                self.wakeProcessTask(taskId);
            }
        });
    }

    /**
     * 异步消费一次开票任务（主路径即时触发；失败由定时兜底重试）。
     *
     * @param taskId 任务主键
     */
    @Async
    public void wakeProcessTask(Long taskId) {
        if (taskId == null) {
            return;
        }
        try {
            issueWorker.processTask(taskId);
        } catch (Exception ex) {
            if (isConcurrentSkip(ex)) {
                log.debug("Boss issue wake skipped (concurrent), taskId={}", taskId);
            } else {
                log.warn("Boss issue wake failed, taskId={}, msg={}", taskId, ex.getMessage());
            }
        }
    }

    private boolean isConcurrentSkip(Exception ex) {
        if (ex instanceof BusinessException businessException) {
            return EinvBossErrorCodes.EINV_TASK_CONCURRENT.equals(businessException.getErrorCode());
        }
        return false;
    }
}
