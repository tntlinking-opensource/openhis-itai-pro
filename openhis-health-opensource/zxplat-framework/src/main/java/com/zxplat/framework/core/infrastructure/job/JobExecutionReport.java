/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.job;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 后台任务执行结果快照，供执行日志等扩展模块消费。
 */
@Getter
@Setter
public class JobExecutionReport {

    /**
     * Spring 容器中 IJob Bean 名称。
     */
    private String jobBeanName;

    /**
     * Quartz JobKey 名称。
     */
    private String quartzJobName;

    /**
     * Quartz JobKey 分组。
     */
    private String quartzJobGroup;

    /**
     * 运维显示名快照。
     */
    private String displayName;

    /**
     * 任务绑定的租户 ID。
     */
    private Long tenantId;

    /**
     * 触发方式 code（如 SCHEDULED / MANUAL）。
     */
    private String executionType;

    /**
     * 任务参数快照（不含框架保留键时可由消费方自行过滤）。
     */
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * 是否执行成功。
     */
    private boolean success;

    /**
     * 失败时的错误摘要。
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）。
     */
    private long durationMs;

    /**
     * 开始执行时间。
     */
    private LocalDateTime startedAt;

    /**
     * 执行完成时间。
     */
    private LocalDateTime finishedAt;
}
