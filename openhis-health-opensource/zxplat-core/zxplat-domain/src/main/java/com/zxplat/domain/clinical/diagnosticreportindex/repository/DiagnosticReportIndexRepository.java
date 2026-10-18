/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.diagnosticreportindex.repository;

import com.zxplat.domain.clinical.diagnosticreportindex.entity.DiagnosticReportIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 报告读侧索引仓储（DM-CLN-006；表 catalog）。
 */
public interface DiagnosticReportIndexRepository extends JpaRepository<DiagnosticReportIndex, Long> {

    /**
     * 按幂等键读取未软删行。
     *
     * @param uniqueKey 唯一键
     * @return 索引行
     */
    Optional<DiagnosticReportIndex> findByUniqueKeyAndIsDeletedFalse(String uniqueKey);
}
