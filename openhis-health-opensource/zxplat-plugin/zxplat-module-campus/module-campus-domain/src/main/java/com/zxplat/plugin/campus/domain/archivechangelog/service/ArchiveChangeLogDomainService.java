/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.archivechangelog.service;

import com.zxplat.plugin.campus.domain.archivechangelog.entity.ArchiveChangeLog;
import com.zxplat.plugin.campus.domain.archivechangelog.repository.ArchiveChangeLogRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 档案变更记录写侧（append-only）。
 */
@Service
@RequiredArgsConstructor
public class ArchiveChangeLogDomainService {

    private final ArchiveChangeLogRepository repository;

    public ArchiveChangeLog append(ArchiveChangeLog entry, Long operatorId) {
        Long tenantId = resolveTenantId(entry.getTenantId());
        entry.setTenantId(tenantId);
        validateEntry(entry);
        if (entry.getBatchId() == null || entry.getBatchId().isBlank()) {
            entry.setBatchId(UUID.randomUUID().toString());
        }
        if (entry.getChangeTime() == null) {
            entry.setChangeTime(LocalDateTime.now());
        }
        entry.setCreatedAt(LocalDateTime.now());
        entry.setCreatedBy(operatorId);
        if (entry.getOperatorId() == null) {
            entry.setOperatorId(operatorId);
        }
        return repository.save(entry);
    }

    public List<ArchiveChangeLog> appendBatch(List<ArchiveChangeLog> entries, Long operatorId) {
        if (entries == null || entries.isEmpty()) {
            throw new BusinessException("CHANGE_LOG_EMPTY", "变更记录不能为空");
        }
        String batchId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        List<ArchiveChangeLog> saved = new ArrayList<>();
        for (ArchiveChangeLog entry : entries) {
            Long tenantId = resolveTenantId(entry.getTenantId());
            entry.setTenantId(tenantId);
            validateEntry(entry);
            entry.setBatchId(batchId);
            if (entry.getChangeTime() == null) {
                entry.setChangeTime(now);
            }
            entry.setCreatedAt(now);
            entry.setCreatedBy(operatorId);
            if (entry.getOperatorId() == null) {
                entry.setOperatorId(operatorId);
            }
            saved.add(repository.save(entry));
        }
        return saved;
    }

    private void validateEntry(ArchiveChangeLog entry) {
        if (entry.getSubjectTypeEnum() == null || entry.getSubjectId() == null) {
            throw new BusinessException("CHANGE_LOG_SUBJECT_REQUIRED", "变更主体不能为空");
        }
        if (entry.getChangeTypeEnum() == null || entry.getFieldName() == null) {
            throw new BusinessException("CHANGE_LOG_FIELD_REQUIRED", "变更类型与字段名不能为空");
        }
        if (entry.getChangeSourceEnum() == null) {
            throw new BusinessException("CHANGE_LOG_SOURCE_REQUIRED", "变更来源不能为空");
        }
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
