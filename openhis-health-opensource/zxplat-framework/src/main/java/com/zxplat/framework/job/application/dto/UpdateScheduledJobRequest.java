/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.application.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新定时任务请求。
 */
@Data
public class UpdateScheduledJobRequest {

    /** 原任务名（定位 zxplat_qrtz_job_details）。 */
    private String jobName;

    /** 原任务组。 */
    private String jobGroup;

    /** 新任务名（不变则与 jobName 相同）。 */
    private String newJobName;

    /** 新任务组。 */
    private String newJobGroup;

    /** 运维显示名。 */
    private String displayName;

    /** 任务说明。 */
    private String description;

    /** 备注。 */
    private String remark;

    /** IJob Spring Bean 名称。 */
    private String jobBeanName;

    /** Cron 表达式。 */
    private String cronExpression;

    /** Cron 可视化预设类型 code。 */
    private String cronPresetType;

    /** 预设：间隔分钟。 */
    private Integer intervalMinutes;

    /** 预设：分钟。 */
    private Integer minute;

    /** 预设：小时。 */
    private Integer hour;

    /** 预设：星期。 */
    private Integer dayOfWeek;

    /** 预设：日。 */
    private Integer dayOfMonth;

    /** Misfire 策略 code。 */
    private String misfirePolicy;

    /** 并发策略 code。 */
    private String concurrentPolicy;

    /** 状态 code。 */
    private String status;

    /** 可选租户 ID。 */
    private Long tenantId;

    /** 业务参数。 */
    private List<ScheduledJobParameterDto> parameters = new ArrayList<>();
}
