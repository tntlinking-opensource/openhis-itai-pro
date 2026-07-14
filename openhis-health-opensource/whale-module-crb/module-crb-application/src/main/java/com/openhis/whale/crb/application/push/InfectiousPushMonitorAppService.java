/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application.push;

import com.openhis.whale.crb.domain.push.CrbEmrPushLog;
import com.openhis.whale.crb.domain.push.CrbEmrPushLogRepository;
import com.openhis.whale.crb.domain.push.CrbEmrPushTask;
import com.openhis.whale.crb.domain.push.CrbEmrPushTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 推送监控 AppService（POST /api/app/infectiousPushMonitor/{methodName}）。
 */
@Service
@Validated
@RequiredArgsConstructor
public class InfectiousPushMonitorAppService {

    private final CrbEmrPushTaskRepository taskRepository;
    private final CrbEmrPushLogRepository logRepository;

    /**
     * 分页查询待处理推送任务（监控首页）。
     *
     * @param tenantId 租户 id
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 任务摘要列表
     */
    public List<PushTaskSummaryDto> listPendingTasks(Long tenantId, int pageNo, int pageSize) {
        // Gate：租户必填，否则监控范围不明确
        if (tenantId == null) {
            return List.of();
        }
        int safePageNo = pageNo;
        if (safePageNo < 1) {
            safePageNo = 1;
        }
        int safePageSize = pageSize;
        if (safePageSize < 1) {
            safePageSize = 20;
        }
        if (safePageSize > 100) {
            safePageSize = 100;
        }
        // Pre：按创建时间倒序取 pending 任务
        PageRequest page = PageRequest.of(safePageNo - 1, safePageSize);
        return taskRepository.findAll(
                        (root, query, cb) -> cb.and(
                                cb.equal(root.get("tenantId"), tenantId),
                                cb.equal(root.get("status"), "PENDING"),
                                cb.equal(root.get("isDeleted"), false)),
                        page)
                .map(this::toTaskSummary)
                .getContent();
    }

    /**
     * 按任务 id 查询推送日志。
     *
     * @param tenantId 租户 id
     * @param taskId 任务 id
     * @return 日志摘要列表
     */
    public List<PushLogSummaryDto> listLogsByTask(Long tenantId, Long taskId) {
        if (tenantId == null || taskId == null) {
            return List.of();
        }
        return logRepository.findAll(
                        (root, query, cb) -> cb.and(
                                cb.equal(root.get("tenantId"), tenantId),
                                cb.equal(root.get("taskId"), taskId),
                                cb.equal(root.get("isDeleted"), false)))
                .stream()
                .map(this::toLogSummary)
                .toList();
    }

    private PushTaskSummaryDto toTaskSummary(CrbEmrPushTask task) {
        PushTaskSummaryDto dto = new PushTaskSummaryDto();
        dto.setTaskId(task.getId());
        dto.setResource(task.getResource());
        dto.setBusinessKey(task.getBusinessKey());
        dto.setStatus(task.getStatus());
        dto.setSlaTier(task.getSlaTier());
        dto.setDueAt(task.getDueAt());
        dto.setAttemptCount(task.getAttemptCount());
        dto.setLastError(task.getLastError());
        return dto;
    }

    private PushLogSummaryDto toLogSummary(CrbEmrPushLog log) {
        PushLogSummaryDto dto = new PushLogSummaryDto();
        dto.setLogId(log.getId());
        dto.setSuccess(log.getSuccess());
        dto.setErrorCode(log.getErrorCode());
        dto.setErrorName(log.getErrorName());
        dto.setDurationMs(log.getDurationMs());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    /** 推送任务监控摘要。 */
    public static class PushTaskSummaryDto {

        /** 任务 id。 */
        private Long taskId;

        /** receive resource。 */
        private String resource;

        /** 业务幂等键。 */
        private String businessKey;

        /** 队列状态 code。 */
        private String status;

        /** SLA 分档 code。 */
        private String slaTier;

        /** SLA 截止时刻。 */
        private java.time.LocalDateTime dueAt;

        /** 已重试次数。 */
        private Integer attemptCount;

        /** 最近错误摘要。 */
        private String lastError;

        public Long getTaskId() {
            return taskId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getBusinessKey() {
            return businessKey;
        }

        public void setBusinessKey(String businessKey) {
            this.businessKey = businessKey;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSlaTier() {
            return slaTier;
        }

        public void setSlaTier(String slaTier) {
            this.slaTier = slaTier;
        }

        public java.time.LocalDateTime getDueAt() {
            return dueAt;
        }

        public void setDueAt(java.time.LocalDateTime dueAt) {
            this.dueAt = dueAt;
        }

        public Integer getAttemptCount() {
            return attemptCount;
        }

        public void setAttemptCount(Integer attemptCount) {
            this.attemptCount = attemptCount;
        }

        public String getLastError() {
            return lastError;
        }

        public void setLastError(String lastError) {
            this.lastError = lastError;
        }
    }

    /** 推送日志监控摘要。 */
    public static class PushLogSummaryDto {

        /** 日志 id。 */
        private Long logId;

        /** 是否成功。 */
        private Boolean success;

        /** 错误码。 */
        private String errorCode;

        /** 错误名。 */
        private String errorName;

        /** 耗时毫秒。 */
        private Long durationMs;

        /** 记录时间。 */
        private java.time.LocalDateTime createdAt;

        public Long getLogId() {
            return logId;
        }

        public void setLogId(Long logId) {
            this.logId = logId;
        }

        public Boolean getSuccess() {
            return success;
        }

        public void setSuccess(Boolean success) {
            this.success = success;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getErrorName() {
            return errorName;
        }

        public void setErrorName(String errorName) {
            this.errorName = errorName;
        }

        public Long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(Long durationMs) {
            this.durationMs = durationMs;
        }

        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
