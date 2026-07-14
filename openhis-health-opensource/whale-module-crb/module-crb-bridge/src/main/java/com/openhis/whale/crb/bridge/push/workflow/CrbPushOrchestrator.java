/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.workflow;

import com.openhis.whale.crb.domain.push.command.EmrPushEnqueueCommand;
import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.domain.share.CrbPushConstants;
import com.openhis.whale.crb.domain.enums.PushStatus;
import com.openhis.whale.crb.domain.push.CrbEmrPushDomainService;
import com.openhis.whale.crb.domain.push.CrbEmrPushTask;
import com.openhis.whale.crb.domain.push.CrbEmrPushTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EMR 推送编排：入队、SLA 调度、Worker 触发、幂等消费。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrbPushOrchestrator {

    private final CrbEmrPushDomainService pushDomainService;
    private final CrbEmrPushTaskRepository taskRepository;
    private final CrbEmrPushWorker pushWorker;
    private final CrbProperties crbProperties;

    /**
     * 异步入队并调度 Worker（替代已废止的 {@code CrbEmrPushClient}）。
     */
    public void enqueueAndSchedule(EmrPushEnqueueCommand command) {
        CrbEmrPushTask task = pushDomainService.enqueue(command);
        scheduleIfDue(task);
    }

    /**
     * 入队后若已到期则异步触发 Worker（REALTIME 立即处理）。
     */
    @Async
    public void scheduleIfDue(CrbEmrPushTask task) {
        if (task == null) {
            return;
        }
        if (!PushStatus.PENDING.getCode().equals(task.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (task.getDueAt() != null && !task.getDueAt().isAfter(now)) {
            pushWorker.process(task.getId());
        }
    }

    /**
     * 扫描指定租户到期任务并消费（定时任务入口，P0 骨架供手动/调度调用）。
     */
    public void drainDueTasks(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<CrbEmrPushTask> dueTasks = taskRepository
                .findTop50ByTenantIdAndStatusAndDueAtLessThanEqualAndIsDeletedFalseOrderByDueAtAsc(
                        tenantId, PushStatus.PENDING.getCode(), now);
        for (CrbEmrPushTask task : dueTasks) {
            pushWorker.process(task.getId());
        }
    }

    public int maxRetry() {
        int configured = crbProperties.getPush().getMaxRetry();
        if (configured < 1) {
            return CrbPushConstants.DEFAULT_MAX_RETRY;
        }
        return configured;
    }
}
