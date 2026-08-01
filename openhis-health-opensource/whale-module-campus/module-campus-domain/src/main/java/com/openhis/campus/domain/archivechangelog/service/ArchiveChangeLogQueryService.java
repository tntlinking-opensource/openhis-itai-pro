/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.archivechangelog.service;

import com.openhis.campus.domain.archivechangelog.entity.ArchiveChangeLog;
import com.openhis.campus.domain.archivechangelog.repository.ArchiveChangeLogRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 档案变更记录读侧。
 */
@Service
@RequiredArgsConstructor
public class ArchiveChangeLogQueryService {

    private final ArchiveChangeLogRepository repository;

    public Page<ArchiveChangeLog> pageBySubject(
        Long tenantId,
        String subjectTypeEnum,
        Long subjectId,
        Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || subjectTypeEnum == null || subjectId == null) {
            return Page.empty(pageable);
        }
        return repository.findByTenantIdAndSubjectTypeEnumAndSubjectIdOrderByChangeTimeDesc(
            resolvedTenantId, subjectTypeEnum, subjectId, pageable);
    }

    public List<ArchiveChangeLog> listByBatchId(Long tenantId, String batchId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || batchId == null || batchId.isBlank()) {
            return List.of();
        }
        return repository.findByTenantIdAndBatchIdOrderByIdAsc(resolvedTenantId, batchId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
