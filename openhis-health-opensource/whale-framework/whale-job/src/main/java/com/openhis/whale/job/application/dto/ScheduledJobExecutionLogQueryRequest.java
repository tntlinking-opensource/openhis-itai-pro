/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import com.openhis.whale.core.application.dto.BaseQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志分页查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduledJobExecutionLogQueryRequest extends BaseQueryRequest {

    /** 租户 ID（为空查全部）。 */
    private Long tenantId;

    /** 任务名精确匹配。 */
    private String jobName;

    /** 任务组精确匹配。 */
    private String jobGroup;

    /** 是否成功。 */
    private Boolean success;

    /** 执行完成时间起（含）。 */
    private LocalDateTime finishedAtFrom;

    /** 执行完成时间止（含）。 */
    private LocalDateTime finishedAtTo;

    /** 关键词（任务名/显示名/Bean）。 */
    private String keyword;
}
