/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.push;

import com.openhis.whale.crb.domain.push.command.EmrPushEnqueueCommand;
import com.openhis.whale.crb.domain.push.command.MarkManualRetryCommand;
import com.openhis.whale.crb.domain.enums.EmrPushSlaTier;
import com.openhis.whale.crb.domain.enums.PushStatus;
import com.openhis.whale.crb.domain.push.result.PushResult;
import com.openhis.whale.crb.domain.share.events.push.CrbEmrPushTaskRetryQueuedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 推送任务入队与状态迁移（crb_emr_push_task / crb_emr_push_log）。
 */
@Service
@RequiredArgsConstructor
public class CrbEmrPushDomainService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final CrbEmrPushTaskRepository taskRepository;
    private final CrbEmrPushLogRepository logRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 入队或合并 UPSERT 任务（同一 resource+businessKey+operation 仅保留最新）。
     *
     * @param command 入队命令
     * @return 持久化后的任务
     */
    @Transactional
    public CrbEmrPushTask enqueue(EmrPushEnqueueCommand command) {
        // 必填项缺失则无法保证幂等与 SLA，直接拒绝入队
        if (command == null || command.getTenantId() == null
                || command.getResource() == null || command.getBusinessKey() == null
                || command.getSlaTier() == null || command.getOperation() == null) {
            throw new IllegalArgumentException("推送入队命令缺少 tenantId/resource/businessKey/slaTier/operation");
        }

        String operationCode = command.getOperation().getCode();
        Optional<CrbEmrPushTask> existing = taskRepository
                .findFirstByTenantIdAndResourceAndBusinessKeyAndOperationAndIsDeletedFalse(
                        command.getTenantId(),
                        command.getResource(),
                        command.getBusinessKey(),
                        operationCode);

        CrbEmrPushTask task;
        if (existing.isPresent()) {
            task = existing.get();
        } else {
            task = new CrbEmrPushTask();
            task.setTenantId(command.getTenantId());
            task.setResource(command.getResource());
            task.setBusinessKey(command.getBusinessKey());
            task.setOperation(operationCode);
            task.setAttemptCount(0);
            task.setIsDeleted(false);
            task.setCreatedAt(LocalDateTime.now());
        }

        task.setSlaTier(command.getSlaTier().getCode());
        task.setStatus(PushStatus.PENDING.getCode());
        task.setDueAt(resolveDueAt(command.getSlaTier(), command.getOperationTime()));
        task.setOperationTime(toLocalDateTime(command.getOperationTime()));
        task.setSourceId(command.getSourceId());
        task.setOperatorId(command.getOperatorId());
        task.setLastError(null);
        task.setUpdatedAt(LocalDateTime.now());

        return taskRepository.save(task);
    }

    /**
     * 记录一次 HTTP 推送结果并更新任务状态。
     *
     * @param task 任务
     * @param result 前置响应
     * @param requestSummary 请求摘要
     * @param responseSummary 响应摘要
     * @param durationMs 耗时毫秒
     * @param maxRetry 最大重试次数
     */
    @Transactional
    public void recordPushAttempt(
            CrbEmrPushTask task,
            PushResult result,
            String requestSummary,
            String responseSummary,
            long durationMs,
            int maxRetry) {

        CrbEmrPushLog log = new CrbEmrPushLog();
        log.setTenantId(task.getTenantId());
        log.setTaskId(task.getId());
        log.setResource(task.getResource());
        log.setBusinessKey(task.getBusinessKey());
        log.setRequestSummary(requestSummary);
        log.setResponseSummary(responseSummary);
        log.setDurationMs(durationMs);
        log.setIsDeleted(false);
        log.setCreatedAt(LocalDateTime.now());

        int nextAttempt = task.getAttemptCount() + 1;
        task.setAttemptCount(nextAttempt);
        task.setUpdatedAt(LocalDateTime.now());

        if (result != null && result.isSuccess()) {
            log.setSuccess(true);
            task.setStatus(PushStatus.SENT.getCode());
            task.setLastError(null);
        } else {
            log.setSuccess(false);
            if (result != null) {
                log.setErrorCode(result.getErrorCode());
                log.setErrorName(result.getErrorName());
            }
            String errorText = buildErrorText(result);
            task.setLastError(errorText);
            // 超过重试上限则标记 DEAD，否则回到 PENDING 等待 Worker 再次扫描
            if (nextAttempt >= maxRetry) {
                task.setStatus(PushStatus.DEAD.getCode());
            } else {
                task.setStatus(PushStatus.PENDING.getCode());
            }
        }

        logRepository.save(log);
        taskRepository.save(task);
    }

    /**
     * 监控页人工重试：将任务回 PENDING 并清空最近错误（保留 attemptCount）。
     *
     * @param command 重试命令
     * @return 更新后的任务
     */
    @Transactional
    public CrbEmrPushTask markManualRetry(MarkManualRetryCommand command) {
        // 必填项缺失则无法定位任务
        if (command == null || command.getTenantId() == null || command.getTaskId() == null) {
            throw new IllegalArgumentException("人工重试命令缺少 tenantId/taskId");
        }
        CrbEmrPushTask task = taskRepository
                .findByIdAndTenantIdAndIsDeletedFalse(command.getTaskId(), command.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("推送任务不存在或已删除"));
        // 已成功送达的任务不应再次重试
        if (PushStatus.SENT.getCode().equals(task.getStatus())) {
            throw new IllegalStateException("已成功送达的任务不可重试");
        }
        task.setStatus(PushStatus.PENDING.getCode());
        // 立即到期，避免仅依赖 drain 租户配置时重试长期空转
        task.setDueAt(LocalDateTime.now());
        task.setLastError(null);
        task.setUpdatedAt(LocalDateTime.now());
        CrbEmrPushTask saved = taskRepository.save(task);
        applicationEventPublisher.publishEvent(new CrbEmrPushTaskRetryQueuedEvent(saved.getId()));
        return saved;
    }

    /**
     * 监控页人工重试便捷入口。
     *
     * @param taskId 任务 id
     * @param tenantId 租户 id
     * @return 更新后的任务
     */
    @Transactional
    public CrbEmrPushTask markManualRetry(Long taskId, Long tenantId) {
        MarkManualRetryCommand command = new MarkManualRetryCommand();
        command.setTaskId(taskId);
        command.setTenantId(tenantId);
        return markManualRetry(command);
    }

    private static LocalDateTime resolveDueAt(EmrPushSlaTier tier, Instant operationTime) {
        Instant base = operationTime;
        if (base == null) {
            base = Instant.now();
        }
        LocalDateTime due;
        if (tier == EmrPushSlaTier.REALTIME) {
            due = LocalDateTime.ofInstant(base, DEFAULT_ZONE);
        } else if (tier == EmrPushSlaTier.TWO_HOUR) {
            due = LocalDateTime.ofInstant(base, DEFAULT_ZONE).plusHours(2);
        } else {
            LocalDate today = LocalDate.now(DEFAULT_ZONE);
            due = today.atTime(23, 59, 59);
        }
        return due;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, DEFAULT_ZONE);
    }

    private static String buildErrorText(PushResult result) {
        if (result == null) {
            return "前置无响应";
        }
        String code = result.getErrorCode();
        String name = result.getErrorName();
        String desc = result.getDescription();
        StringBuilder sb = new StringBuilder();
        if (code != null && !code.isBlank()) {
            sb.append(code);
        }
        if (name != null && !name.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(": ");
            }
            sb.append(name);
        }
        if (desc != null && !desc.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" — ");
            }
            sb.append(desc);
        }
        if (sb.isEmpty()) {
            return "推送失败";
        }
        return sb.toString();
    }
}
