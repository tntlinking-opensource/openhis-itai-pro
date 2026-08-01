/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.event;

import com.openhis.whale.crb.bridge.push.workflow.CrbPushOrchestrator;
import com.openhis.whale.crb.domain.push.CrbEmrPushTask;
import com.openhis.whale.crb.domain.push.CrbEmrPushTaskRepository;
import com.openhis.whale.crb.domain.share.events.push.CrbEmrPushTaskRetryQueuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * 人工重试入队后立即调度 Worker（不依赖 drain-tenant-id 是否配置）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrbPushRetryQueuedListener {

    private final CrbEmrPushTaskRepository taskRepository;
    private final CrbPushOrchestrator pushOrchestrator;

    /**
     * 事务提交后触发调度，避免未提交状态被 Worker 读到。
     *
     * @param event 重试入队事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRetryQueued(CrbEmrPushTaskRetryQueuedEvent event) {
        // 无事件或无任务 id 则无法调度
        if (event == null || event.getTaskId() == null) {
            return;
        }
        Optional<CrbEmrPushTask> optionalTask = taskRepository.findById(event.getTaskId());
        // 任务已删或不存在则跳过
        if (optionalTask.isEmpty()) {
            log.warn("CRB retry schedule skipped, task missing id={}", event.getTaskId());
            return;
        }
        pushOrchestrator.scheduleIfDue(optionalTask.get());
    }
}
