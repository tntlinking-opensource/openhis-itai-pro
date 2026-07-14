/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.domain;

import com.openhis.whale.core.infrastructure.id.SnowflakeId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 定时任务单次执行历史（append-only，对应 whale_qrtz_job_execution_log）。
 *
 * <p>与 {@code whale_qrtz_fired_triggers} 不同：后者仅存正在执行的瞬时行；本表保留完整历史与成败信息。</p>
 */
@Entity
@Table(name = "whale_qrtz_job_execution_log", indexes = {
        @Index(name = "idx_whale_qrtz_job_exec_log_finished", columnList = "finished_at"),
        @Index(name = "idx_whale_qrtz_job_exec_log_tenant", columnList = "tenant_id"),
        @Index(name = "idx_whale_qrtz_job_exec_log_job_key", columnList = "sched_name, job_group, job_name")
})
@Getter
@Setter
public class ScheduledJobExecutionLog {

    @Id
    @SnowflakeId
    @Column(name = "id", nullable = false)
    private Long id;

    /** Quartz 调度器名称，与 whale_qrtz_job_details.sched_name 一致。 */
    @Column(name = "sched_name", nullable = false, length = 120)
    private String schedName = "WhaleScheduler";

    /** Quartz job_name。 */
    @Column(name = "job_name", nullable = false, length = 200)
    private String jobName;

    /** Quartz job_group。 */
    @Column(name = "job_group", nullable = false, length = 200)
    private String jobGroup;

    /** IJob Spring Bean 名称。 */
    @Column(name = "job_bean_name", nullable = false, length = 128)
    private String jobBeanName;

    /** 执行时的运维显示名快照。 */
    @Column(name = "display_name", length = 128)
    private String displayName;

    /** 任务绑定的租户（可为空表示平台级任务）。 */
    @Column(name = "tenant_id")
    private Long tenantId;

    /** 触发方式 code，见 {@link ScheduledJobExecutionType}。 */
    @Column(name = "execution_type", nullable = false, length = 16)
    private String executionType;

    /** 是否执行成功。 */
    @Column(name = "success", nullable = false)
    private Boolean success;

    /** 失败时的错误摘要。 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 执行耗时（毫秒）。 */
    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    /** 开始执行时间。 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 结束时间。 */
    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;
}
