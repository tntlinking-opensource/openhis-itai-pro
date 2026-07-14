/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务详情（从 whale_qrtz_* 组装）。
 */
@Data
public class ScheduledJobDto {

    /** Quartz 任务名称。 */
    private String jobName;

    /** Quartz 任务分组。 */
    private String jobGroup;

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

    /** Cron 中文描述。 */
    private String cronDescription;

    /** 触发类型：CRON 等。 */
    private String triggerType;

    /** 状态 code：NORMAL / PAUSE。 */
    private String status;

    /** 状态中文。 */
    private String statusLabel;

    /** Misfire 策略 code。 */
    private String misfirePolicy;

    /** Misfire 策略中文。 */
    private String misfirePolicyLabel;

    /** 并发策略 code。 */
    private String concurrentPolicy;

    /** 并发策略中文。 */
    private String concurrentPolicyLabel;

    /** 可选租户 ID。 */
    private Long tenantId;

    /** 租户名称（展示用）。 */
    private String tenantName;

    /** 业务参数。 */
    private List<ScheduledJobParameterDto> parameters = new ArrayList<>();

    /** 下次执行时间。 */
    private LocalDateTime nextFireTime;

    /** 上次执行时间。 */
    private LocalDateTime previousFireTime;
}
