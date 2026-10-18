/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.repository;

import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeComplianceReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 合规报告仓储。
 */
public interface YbCatalogNoticeComplianceReportRepository
    extends JpaRepository<YbCatalogNoticeComplianceReport, Long> {

    /**
     * 取指定契约+类别下最新报告。
     */
    Optional<YbCatalogNoticeComplianceReport>
        findFirstByTenantIdAndContractIdAndListTypeAndIsDeletedFalseOrderByRanAtDesc(
            Long tenantId, Long contractId, String listType);

    /**
     * 取租户下最新报告（无契约/类别过滤）。
     */
    Optional<YbCatalogNoticeComplianceReport>
        findFirstByTenantIdAndIsDeletedFalseOrderByRanAtDesc(Long tenantId);

    /**
     * 按契约取最新（listType 空）。
     */
    Optional<YbCatalogNoticeComplianceReport>
        findFirstByTenantIdAndContractIdAndIsDeletedFalseOrderByRanAtDesc(
            Long tenantId, Long contractId);
}
