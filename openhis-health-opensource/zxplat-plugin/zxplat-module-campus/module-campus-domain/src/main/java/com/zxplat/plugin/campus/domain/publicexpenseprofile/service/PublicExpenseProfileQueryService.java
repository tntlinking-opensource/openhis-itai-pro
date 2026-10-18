/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.publicexpenseprofile.service;

import com.zxplat.plugin.campus.domain.publicexpenseprofile.entity.PublicExpenseProfile;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.repository.PublicExpenseProfileRepository;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 公费资格档案读侧。
 */
@Service
@RequiredArgsConstructor
public class PublicExpenseProfileQueryService {

    private final PublicExpenseProfileRepository repository;

    public Optional<PublicExpenseProfile> getBySubject(Long tenantId, String subjectTypeEnum, Long subjectId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || subjectTypeEnum == null || subjectId == null) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(
            resolvedTenantId, subjectTypeEnum, subjectId);
    }

    public Optional<PublicExpenseProfile> findByCardNo(Long tenantId, String cardNo) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || cardNo == null || cardNo.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndCardNoAndIsDeletedFalse(resolvedTenantId, cardNo);
    }

    public Optional<PublicExpenseProfile> findByPatientId(Long tenantId, Long patientId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || patientId == null) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndPatientIdAndIsDeletedFalse(resolvedTenantId, patientId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
