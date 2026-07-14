/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志条目。
 */
@Data
public class ScheduledJobExecutionLogDto {

    /** 日志主键。 */
    private Long id;

    /** Quartz 任务名。 */
    private String jobName;

    /** Quartz 任务组。 */
    private String jobGroup;

    /** IJob Bean 名称。 */
    private String jobBeanName;

    /** 显示名快照。 */
    private String displayName;

    /** 租户 ID。 */
    private Long tenantId;

    /** 租户名称（关联展示）。 */
    private String tenantName;

    /** 触发方式 code。 */
    private String executionType;

    /** 触发方式中文。 */
    private String executionTypeLabel;

    /** 是否成功。 */
    private Boolean success;

    /** 结果中文。 */
    private String successLabel;

    /** 错误信息。 */
    private String errorMessage;

    /** 耗时毫秒。 */
    private Long durationMs;

    /** 开始时间。 */
    private LocalDateTime startedAt;

    /** 结束时间。 */
    private LocalDateTime finishedAt;
}
