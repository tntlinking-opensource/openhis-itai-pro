/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.archivetransfer.service;

import com.openhis.campus.domain.archivetransfer.entity.ArchiveTransfer;
import com.openhis.campus.domain.archivetransfer.repository.ArchiveTransferRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 档案调动记录读侧。
 */
@Service
@RequiredArgsConstructor
public class ArchiveTransferQueryService {

    private final ArchiveTransferRepository repository;

    public Page<ArchiveTransfer> pageBySubject(
        Long tenantId,
        String subjectTypeEnum,
        Long subjectId,
        Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || subjectTypeEnum == null || subjectId == null) {
            return Page.empty(pageable);
        }
        return repository.findByTenantIdAndSubjectTypeEnumAndSubjectIdOrderByEffectiveDateDescOperateTimeDesc(
            resolvedTenantId, subjectTypeEnum, subjectId, pageable);
    }

    public List<ArchiveTransfer> listBySubject(Long tenantId, String subjectTypeEnum, Long subjectId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || subjectTypeEnum == null || subjectId == null) {
            return List.of();
        }
        return repository.findByTenantIdAndSubjectTypeEnumAndSubjectIdOrderByEffectiveDateDescOperateTimeDesc(
            resolvedTenantId, subjectTypeEnum, subjectId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
