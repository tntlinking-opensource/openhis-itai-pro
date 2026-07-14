/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.support;

import com.openhis.whale.job.application.dto.ScheduledJobParameterDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 Quartz 读取的任务快照，用于列表/详情与写回调度器。
 */
@Getter
@Setter
public class ScheduledJobSnapshot {

    /** Quartz JobKey 名称。 */
    private String jobName;

    /** Quartz JobKey 分组。 */
    private String jobGroup;

    /** 运维显示名。 */
    private String displayName;

    /** 任务说明（JobDetail.description）。 */
    private String description;

    /** 备注。 */
    private String remark;

    /** IJob Spring Bean 名称。 */
    private String jobBeanName;

    /** Cron 表达式；非 Cron 任务可为空。 */
    private String cronExpression;

    /** 触发类型：CRON / SIMPLE / 其他。 */
    private String triggerType;

    /** 运行状态 code，见 {@link com.openhis.whale.job.domain.ScheduledJobStatus}。 */
    private String status;

    /** Misfire 策略 code。 */
    private String misfirePolicy;

    /** 并发策略 code。 */
    private String concurrentPolicy;

    /** 可选租户 ID。 */
    private Long tenantId;

    /** 业务参数（不含框架保留键）。 */
    private List<ScheduledJobParameterDto> parameters = new ArrayList<>();

    /** 下次触发时间。 */
    private LocalDateTime nextFireTime;

    /** 上次触发时间。 */
    private LocalDateTime previousFireTime;
}
