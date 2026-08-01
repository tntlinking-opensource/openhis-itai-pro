/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application.push;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.crb.application.support.CrbConfigMaskSupport;
import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.domain.push.CrbEmrPushDomainService;
import com.openhis.whale.crb.domain.push.CrbEmrPushLog;
import com.openhis.whale.crb.domain.push.CrbEmrPushTask;
import com.openhis.whale.crb.domain.push.queries.CrbEmrPushQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
import static com.openhis.whale.crb.domain.share.CrbPushConstants.DEFAULT_PAGE_SIZE;
import static com.openhis.whale.crb.domain.share.CrbPushConstants.MAX_PAGE_SIZE;

/**
 * 推送监控 AppService（POST /api/app/infectiousPushMonitor/{methodName}）。
 *
 * <p><b>DOC-CONFLICT：</b>AS-CRB-MON-001-SDD 方法名 {@code retryPush}/{@code pagePushLogs} 等全量契约；
 * 本轮实现 {@code retryTask}/{@code pageTasks}/{@code getFrontSoftwareConfigView} 与存量 list* 并存。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class InfectiousPushMonitorAppService {

    private final CrbEmrPushQueryService pushQueryService;
    private final CrbEmrPushDomainService pushDomainService;
    private final CrbProperties crbProperties;

    /**
     * 分页查询待处理推送任务（监控首页，存量接口保留）。
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
        int safePageNo = normalizePageNo(pageNo);
        int safePageSize = normalizePageSize(pageSize);
        // Pre：按更新时间倒序取 PENDING 任务
        PageRequest page = PageRequest.of(
                safePageNo - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt"));
        // Query：领域读侧
        return pushQueryService.pageTasks(tenantId, "PENDING", page)
                .map(this::toTaskSummary)
                .getContent();
    }

    /**
     * 按任务 id 查询推送日志（存量接口保留）。
     *
     * @param tenantId 租户 id
     * @param taskId 任务 id
     * @return 日志摘要列表
     */
    public List<PushLogSummaryDto> listLogsByTask(Long tenantId, Long taskId) {
        // Gate：租户与任务必填
        if (tenantId == null || taskId == null) {
            return List.of();
        }
        // Query + Asm
        return pushQueryService.listLogsByTask(tenantId, taskId).stream()
                .map(this::toLogSummary)
                .toList();
    }

    /**
     * 分页查询推送任务（可按 status 筛选）。
     *
     * @param tenantId 租户 id
     * @param request 分页与筛选条件
     * @return 分页任务摘要
     */
    public PagedResult<PushTaskSummaryDto> pageTasks(Long tenantId, PageTasksRequest request) {
        // Gate：租户与分页必填
        if (tenantId == null) {
            return emptyPage(request);
        }
        PageTasksRequest safeRequest = request;
        if (safeRequest == null) {
            safeRequest = new PageTasksRequest();
        }
        int safePageNo = normalizePageNo(safeRequest.getPageNo());
        int safePageSize = normalizePageSize(safeRequest.getPageSize());
        // Pre：组装分页
        PageRequest page = PageRequest.of(
                safePageNo - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt"));
        // Query：领域读侧分页
        Page<CrbEmrPushTask> taskPage = pushQueryService.pageTasks(tenantId, safeRequest.getStatus(), page);
        // Asm：映射为监控 DTO
        List<PushTaskSummaryDto> content = taskPage.getContent().stream()
                .map(this::toTaskSummary)
                .toList();
        PagedResult<PushTaskSummaryDto> result = new PagedResult<>();
        result.setContent(content);
        result.setTotalCount(taskPage.getTotalElements());
        result.setPageNo(safePageNo);
        result.setPageSize(safePageSize);
        return result;
    }

    /**
     * 人工重试推送任务。
     *
     * @param tenantId 租户 id
     * @param taskId 任务 id
     * @return 重试后任务摘要
     */
    public PushTaskSummaryDto retryTask(Long tenantId, Long taskId) {
        // Gate：租户与任务 id 必填
        if (tenantId == null || taskId == null) {
            throw new IllegalArgumentException("tenantId 与 taskId 不能为空");
        }
        // Cmd：领域人工重试
        CrbEmrPushTask task = pushDomainService.markManualRetry(taskId, tenantId);
        // Asm：返回最新摘要
        return toTaskSummary(task);
    }

    /**
     * 读取前置软件配置只读视图（脱敏 license，不写库）。
     *
     * @return 配置视图
     */
    public FrontSoftwareConfigViewDto getFrontSoftwareConfigView() {
        // Pre：读 yml 绑定
        CrbProperties.FrontSoftware frontSoftware = crbProperties.getFrontSoftware();
        CrbProperties.Push push = crbProperties.getPush();
        FrontSoftwareConfigViewDto dto = new FrontSoftwareConfigViewDto();
        dto.setBaseUrlMasked(CrbConfigMaskSupport.maskBaseUrl(frontSoftware.getBaseUrl()));
        dto.setLicenseMasked(CrbConfigMaskSupport.maskLicense(frontSoftware.getLicense()));
        dto.setOrgCode(trimToNull(frontSoftware.getOrgCode()));
        dto.setMockEnabled(push.isMockEnabled());
        String baseUrl = trimToNull(frontSoftware.getBaseUrl());
        String orgCode = trimToNull(frontSoftware.getOrgCode());
        String license = trimToNull(frontSoftware.getLicense());
        boolean configured = baseUrl != null && orgCode != null && license != null;
        dto.setConfigured(configured);
        return dto;
    }

    /**
     * 归一化页码（从 1 开始）。
     *
     * @param pageNo 原始页码
     * @return 安全页码
     */
    private int normalizePageNo(int pageNo) {
        int safePageNo = pageNo;
        if (safePageNo < 1) {
            safePageNo = 1;
        }
        return safePageNo;
    }

    /**
     * 归一化每页条数。
     *
     * @param pageSize 原始条数
     * @return 安全条数
     */
    private int normalizePageSize(int pageSize) {
        int safePageSize = pageSize;
        if (safePageSize < 1) {
            safePageSize = DEFAULT_PAGE_SIZE;
        }
        if (safePageSize > MAX_PAGE_SIZE) {
            safePageSize = MAX_PAGE_SIZE;
        }
        return safePageSize;
    }

    /**
     * 租户缺失时的空分页。
     *
     * @param request 请求
     * @return 空分页
     */
    private PagedResult<PushTaskSummaryDto> emptyPage(PageTasksRequest request) {
        int pageNo = 1;
        int pageSize = DEFAULT_PAGE_SIZE;
        if (request != null) {
            pageNo = normalizePageNo(request.getPageNo());
            pageSize = normalizePageSize(request.getPageSize());
        }
        PagedResult<PushTaskSummaryDto> empty = new PagedResult<>();
        empty.setContent(List.of());
        empty.setTotalCount(0L);
        empty.setPageNo(pageNo);
        empty.setPageSize(pageSize);
        return empty;
    }

    /**
     * 实体转任务监控摘要。
     *
     * @param task 任务实体
     * @return 摘要 DTO
     */
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

    /**
     * 实体转日志监控摘要。
     *
     * @param log 日志实体
     * @return 摘要 DTO
     */
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

    /** 任务分页请求。 */
    public static class PageTasksRequest {

        /** 页码，从 1 开始。 */
        private int pageNo = 1;

        /** 每页条数。 */
        private int pageSize = DEFAULT_PAGE_SIZE;

        /** 可选状态筛选（PENDING/SENT/DEAD 等）。 */
        private String status;

        /**
         * @return 页码
         */
        public int getPageNo() {
            return pageNo;
        }

        /**
         * @param pageNo 页码
         */
        public void setPageNo(int pageNo) {
            this.pageNo = pageNo;
        }

        /**
         * @return 每页条数
         */
        public int getPageSize() {
            return pageSize;
        }

        /**
         * @param pageSize 每页条数
         */
        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        /**
         * @return 状态筛选
         */
        public String getStatus() {
            return status;
        }

        /**
         * @param status 状态筛选
         */
        public void setStatus(String status) {
            this.status = status;
        }
    }

    /** 前置配置只读视图。 */
    public static class FrontSoftwareConfigViewDto {

        /** 是否 baseUrl/orgCode/license 均已配置。 */
        private boolean configured;

        /** 脱敏 baseUrl。 */
        private String baseUrlMasked;

        /** 脱敏 license。 */
        private String licenseMasked;

        /** 国家机构代码。 */
        private String orgCode;

        /** Mock 推送开关。 */
        private boolean mockEnabled;

        /**
         * @return 是否已配置
         */
        public boolean isConfigured() {
            return configured;
        }

        /**
         * @param configured 是否已配置
         */
        public void setConfigured(boolean configured) {
            this.configured = configured;
        }

        /**
         * @return 脱敏 baseUrl
         */
        public String getBaseUrlMasked() {
            return baseUrlMasked;
        }

        /**
         * @param baseUrlMasked 脱敏 baseUrl
         */
        public void setBaseUrlMasked(String baseUrlMasked) {
            this.baseUrlMasked = baseUrlMasked;
        }

        /**
         * @return 脱敏 license
         */
        public String getLicenseMasked() {
            return licenseMasked;
        }

        /**
         * @param licenseMasked 脱敏 license
         */
        public void setLicenseMasked(String licenseMasked) {
            this.licenseMasked = licenseMasked;
        }

        /**
         * @return 机构代码
         */
        public String getOrgCode() {
            return orgCode;
        }

        /**
         * @param orgCode 机构代码
         */
        public void setOrgCode(String orgCode) {
            this.orgCode = orgCode;
        }

        /**
         * @return Mock 开关
         */
        public boolean isMockEnabled() {
            return mockEnabled;
        }

        /**
         * @param mockEnabled Mock 开关
         */
        public void setMockEnabled(boolean mockEnabled) {
            this.mockEnabled = mockEnabled;
        }
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

        /**
         * @return 任务 id
         */
        public Long getTaskId() {
            return taskId;
        }

        /**
         * @param taskId 任务 id
         */
        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        /**
         * @return resource
         */
        public String getResource() {
            return resource;
        }

        /**
         * @param resource resource
         */
        public void setResource(String resource) {
            this.resource = resource;
        }

        /**
         * @return 业务键
         */
        public String getBusinessKey() {
            return businessKey;
        }

        /**
         * @param businessKey 业务键
         */
        public void setBusinessKey(String businessKey) {
            this.businessKey = businessKey;
        }

        /**
         * @return 状态
         */
        public String getStatus() {
            return status;
        }

        /**
         * @param status 状态
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * @return SLA 分档
         */
        public String getSlaTier() {
            return slaTier;
        }

        /**
         * @param slaTier SLA 分档
         */
        public void setSlaTier(String slaTier) {
            this.slaTier = slaTier;
        }

        /**
         * @return 截止时刻
         */
        public java.time.LocalDateTime getDueAt() {
            return dueAt;
        }

        /**
         * @param dueAt 截止时刻
         */
        public void setDueAt(java.time.LocalDateTime dueAt) {
            this.dueAt = dueAt;
        }

        /**
         * @return 尝试次数
         */
        public Integer getAttemptCount() {
            return attemptCount;
        }

        /**
         * @param attemptCount 尝试次数
         */
        public void setAttemptCount(Integer attemptCount) {
            this.attemptCount = attemptCount;
        }

        /**
         * @return 最近错误
         */
        public String getLastError() {
            return lastError;
        }

        /**
         * @param lastError 最近错误
         */
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

        /**
         * @return 日志 id
         */
        public Long getLogId() {
            return logId;
        }

        /**
         * @param logId 日志 id
         */
        public void setLogId(Long logId) {
            this.logId = logId;
        }

        /**
         * @return 是否成功
         */
        public Boolean getSuccess() {
            return success;
        }

        /**
         * @param success 是否成功
         */
        public void setSuccess(Boolean success) {
            this.success = success;
        }

        /**
         * @return 错误码
         */
        public String getErrorCode() {
            return errorCode;
        }

        /**
         * @param errorCode 错误码
         */
        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        /**
         * @return 错误名
         */
        public String getErrorName() {
            return errorName;
        }

        /**
         * @param errorName 错误名
         */
        public void setErrorName(String errorName) {
            this.errorName = errorName;
        }

        /**
         * @return 耗时毫秒
         */
        public Long getDurationMs() {
            return durationMs;
        }

        /**
         * @param durationMs 耗时毫秒
         */
        public void setDurationMs(Long durationMs) {
            this.durationMs = durationMs;
        }

        /**
         * @return 记录时间
         */
        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }

        /**
         * @param createdAt 记录时间
         */
        public void setCreatedAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
