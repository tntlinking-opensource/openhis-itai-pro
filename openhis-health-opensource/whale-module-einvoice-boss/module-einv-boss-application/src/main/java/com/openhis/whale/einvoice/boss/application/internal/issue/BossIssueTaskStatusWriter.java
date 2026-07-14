/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.domain.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开票任务状态持久化（编程式 REQUIRES_NEW，与 AppServiceAspect 手动事务兼容）。
 *
 * <p>Worker 主事务可能回滚，任务状态（PROCESSING/SUCCESS/FAILED）须在独立事务中提交，
 * 避免「博思已开票但本地任务仍 PENDING」或「抢占泄漏」。</p>
 */
@Service
@RequiredArgsConstructor
public class BossIssueTaskStatusWriter {

    /** 失败退避上限（分钟），{@code attemptCount * 2}  capped 于此值。 */
    private static final int MAX_BACKOFF_MINUTES = 60;

    private final EinvBossIssueTaskRepository taskRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 原子抢占任务：蓝绿多实例下仅一个节点可将 PENDING/FAILED 置为 PROCESSING。
     *
     * @param taskId 任务主键
     * @return 是否抢占成功
     */
    public boolean tryClaimProcessing(Long taskId) {
        return Boolean.TRUE.equals(runInNewTransaction(() -> {
            int updated = taskRepository.claimForProcessing(
                    taskId,
                    TaskStatus.PROCESSING.getCode(),
                    List.of(TaskStatus.PENDING.getCode(), TaskStatus.FAILED.getCode()),
                    LocalDateTime.now());
            return updated > 0;
        }));
    }

    /**
     * 标记任务成功（独立提交）。
     */
    public void markSuccess(Long taskId, String message) {
        runInNewTransaction(() -> {
            EinvBossIssueTask task = loadTask(taskId);
            task.setStatus(TaskStatus.SUCCESS.getCode());
            task.setLastErrorCode(null);
            task.setLastErrorMsg(message);
            task.setNextRetryAt(null);
            taskRepository.saveAndFlush(task);
        });
    }

    /**
     * 标记任务失败并设置退避（独立提交）。
     */
    public void failTask(Long taskId, String errorCode, String errorMsg) {
        runInNewTransaction(() -> {
            EinvBossIssueTask task = loadTask(taskId);
            task.setStatus(TaskStatus.FAILED.getCode());
            task.setLastErrorCode(errorCode);
            task.setLastErrorMsg(errorMsg);
            int backoff = Math.min(task.getAttemptCount() * 2, MAX_BACKOFF_MINUTES);
            task.setNextRetryAt(LocalDateTime.now().plusMinutes(backoff));
            taskRepository.saveAndFlush(task);
        });
    }

    /**
     * 按主键加载任务，不存在时抛 {@link EinvBossErrorCodes#EINV_TASK_NOT_FOUND}。
     *
     * @param taskId 任务主键
     * @return 任务实体
     */
    private EinvBossIssueTask loadTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "任务不存在"));
    }

    /**
     * 在独立新事务中执行无返回值动作（PROPAGATION_REQUIRES_NEW）。
     *
     * @param action 状态更新逻辑
     */
    private void runInNewTransaction(Runnable action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> action.run());
    }

    /**
     * 在独立新事务中执行有返回值动作。
     *
     * @param action 状态更新或抢占逻辑
     * @param <T> 返回值类型
     * @return action 执行结果
     */
    private <T> T runInNewTransaction(java.util.function.Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }
}
