/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.diagnosticreport.repository;

import com.zxplat.plugin.pacs.domain.diagnosticreport.entity.PacsDiagnosticReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 检查报告仓储。
 */
public interface PacsDiagnosticReportRepository
    extends JpaRepository<PacsDiagnosticReport, Long>, JpaSpecificationExecutor<PacsDiagnosticReport> {

    /**
     * 按工单取主报告（amendedFromId 为空；底座已滤软删）。
     *
     * @param workItemId 工单主键
     * @return 主报告
     */
    Optional<PacsDiagnosticReport> findByWorkItemIdAndAmendedFromIdIsNull(Long workItemId);
}
