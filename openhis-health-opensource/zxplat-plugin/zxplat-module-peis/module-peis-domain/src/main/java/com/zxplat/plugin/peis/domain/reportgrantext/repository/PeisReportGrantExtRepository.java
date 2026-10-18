/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.reportgrantext.repository;

import com.zxplat.plugin.peis.domain.reportgrantext.entity.PeisReportGrantExt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 报告发放扩展仓储（DM-PEIS-005）。
 */
public interface PeisReportGrantExtRepository
    extends JpaRepository<PeisReportGrantExt, Long>, JpaSpecificationExecutor<PeisReportGrantExt> {

    /**
     * 按总检报告主键读取未软删行。
     *
     * @param diagnosticReportId 总检报告主键
     * @return 实体（可空）
     */
    Optional<PeisReportGrantExt> findByDiagnosticReportIdAndIsDeletedFalse(Long diagnosticReportId);
}
