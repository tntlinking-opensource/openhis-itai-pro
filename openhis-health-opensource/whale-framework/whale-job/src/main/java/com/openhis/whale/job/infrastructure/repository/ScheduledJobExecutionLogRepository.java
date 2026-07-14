/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.infrastructure.repository;

import com.openhis.whale.job.domain.ScheduledJobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * 定时任务执行日志仓储。
 */
@Repository
public interface ScheduledJobExecutionLogRepository
        extends JpaRepository<ScheduledJobExecutionLog, Long>, JpaSpecificationExecutor<ScheduledJobExecutionLog> {
}
