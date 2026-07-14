/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.print.printlog.repository;

import com.openhis.health.domain.print.printlog.entity.PrtPrintEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 打印审计日志仓储（PrtPrintEventLog 聚合，append-only）。
 */
public interface PrtPrintEventLogRepository
    extends JpaRepository<PrtPrintEventLog, Long>, JpaSpecificationExecutor<PrtPrintEventLog> {
}
