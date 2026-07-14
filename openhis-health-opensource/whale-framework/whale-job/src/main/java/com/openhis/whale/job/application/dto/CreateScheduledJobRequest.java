/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 新建定时任务请求（写入 whale_qrtz_*）。
 */
@Data
public class CreateScheduledJobRequest {

    /** Quartz 任务名称（唯一标识，建议英文/数字）。 */
    private String jobName;

    /** Quartz 任务分组，默认 DEFAULT。 */
    private String jobGroup;

    /** 运维显示名。 */
    private String displayName;

    /** 任务说明。 */
    private String description;

    /** 备注。 */
    private String remark;

    /** IJob Spring Bean 名称。 */
    private String jobBeanName;

    /** Cron 表达式（与 cronPreset 二选一，优先表达式）。 */
    private String cronExpression;

    /** Cron 可视化预设类型 code。 */
    private String cronPresetType;

    /** 预设：间隔分钟。 */
    private Integer intervalMinutes;

    /** 预设：分钟。 */
    private Integer minute;

    /** 预设：小时。 */
    private Integer hour;

    /** 预设：星期（1=周日）。 */
    private Integer dayOfWeek;

    /** 预设：日。 */
    private Integer dayOfMonth;

    /** Misfire 策略 code。 */
    private String misfirePolicy;

    /** 并发策略 code。 */
    private String concurrentPolicy;

    /** 初始状态 code，默认 PAUSE（新建后暂停，确认无误再启用）。 */
    private String status;

    /** 可选租户 ID。 */
    private Long tenantId;

    /** 业务参数。 */
    private List<ScheduledJobParameterDto> parameters = new ArrayList<>();
}
