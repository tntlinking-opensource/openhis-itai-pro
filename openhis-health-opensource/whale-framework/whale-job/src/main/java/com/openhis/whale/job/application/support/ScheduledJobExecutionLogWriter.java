/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.support;

import com.openhis.whale.core.infrastructure.job.IJobExecutionListener;
import com.openhis.whale.core.infrastructure.job.JobExecutionReport;
import com.openhis.whale.job.domain.ScheduledJobExecutionLog;
import com.openhis.whale.job.domain.ScheduledJobExecutionType;
import com.openhis.whale.job.infrastructure.repository.ScheduledJobExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 将 Quartz 任务执行结果写入 whale_qrtz_job_execution_log。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobExecutionLogWriter implements IJobExecutionListener {

    private final ScheduledJobExecutionLogRepository scheduledJobExecutionLogRepository;

    /**
     * 任务结束后持久化执行日志。
     *
     * @param report Quartz 适配器产出的执行快照
     */
    @Override
    public void onJobFinished(JobExecutionReport report) {
        ScheduledJobExecutionLog entity = new ScheduledJobExecutionLog();
        entity.setJobName(report.getQuartzJobName());
        entity.setJobGroup(report.getQuartzJobGroup());
        entity.setJobBeanName(report.getJobBeanName());
        entity.setDisplayName(trimToNull(report.getDisplayName()));
        entity.setTenantId(report.getTenantId());
        String executionType = trimToNull(report.getExecutionType());
        if (executionType == null) {
            executionType = ScheduledJobExecutionType.SCHEDULED.getCode();
        }
        entity.setExecutionType(executionType);
        entity.setSuccess(report.isSuccess());
        entity.setErrorMessage(trimToNull(report.getErrorMessage()));
        entity.setDurationMs(report.getDurationMs());
        LocalDateTime startedAt = report.getStartedAt();
        if (startedAt == null) {
            startedAt = report.getFinishedAt();
        }
        entity.setStartedAt(startedAt);
        entity.setFinishedAt(report.getFinishedAt());
        scheduledJobExecutionLogRepository.save(entity);
        log.debug("Saved scheduled job execution log: {} ({}) success={}",
                entity.getJobName(), entity.getJobGroup(), entity.getSuccess());
    }
}
