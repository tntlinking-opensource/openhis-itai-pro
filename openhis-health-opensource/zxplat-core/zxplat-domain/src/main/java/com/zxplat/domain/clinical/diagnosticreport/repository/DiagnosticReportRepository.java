/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.diagnosticreport.repository;

import com.zxplat.domain.clinical.diagnosticreport.entity.DiagnosticReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 临床总检报告仓储（DM-CLN-008 §4.5）。
 */
public interface DiagnosticReportRepository extends JpaRepository<DiagnosticReport, Long> {

    /**
     * 按就诊读取未软删主报告。
     *
     * @param encounterId 就诊主键
     * @return 报告行
     */
    Optional<DiagnosticReport> findByEncounterIdAndIsDeletedFalse(Long encounterId);

    /**
     * 就诊是否已有主报告行。
     *
     * @param encounterId 就诊主键
     * @return 存在为 true
     */
    boolean existsByEncounterIdAndIsDeletedFalse(Long encounterId);
}
