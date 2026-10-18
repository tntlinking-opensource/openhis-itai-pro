/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.archivetransfer.service;

import com.zxplat.plugin.campus.domain.archivetransfer.entity.ArchiveTransfer;
import com.zxplat.plugin.campus.domain.archivetransfer.repository.ArchiveTransferRepository;
import com.zxplat.plugin.campus.domain.shared.CampusDomainConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 档案调动记录写侧。
 */
@Service
@RequiredArgsConstructor
public class ArchiveTransferDomainService {

    private final ArchiveTransferRepository repository;

    public ArchiveTransfer recordStudentTransfer(ArchiveTransfer transfer, Long operatorId) {
        Long tenantId = resolveTenantId(transfer.getTenantId());
        transfer.setTenantId(tenantId);
        transfer.setSubjectTypeEnum(CampusDomainConstants.SUBJECT_STUDENT);
        validateOrgDiff(transfer);
        if (transfer.getEffectiveDate() == null) {
            transfer.setEffectiveDate(LocalDate.now());
        }
        return persist(transfer, operatorId);
    }

    public ArchiveTransfer recordStaffTransfer(ArchiveTransfer transfer, Long operatorId) {
        Long tenantId = resolveTenantId(transfer.getTenantId());
        transfer.setTenantId(tenantId);
        transfer.setSubjectTypeEnum(CampusDomainConstants.SUBJECT_STAFF);
        validateOrgDiff(transfer);
        if (transfer.getEffectiveDate() == null) {
            transfer.setEffectiveDate(LocalDate.now());
        }
        return persist(transfer, operatorId);
    }

    public ArchiveTransfer recordDerivedFromUpdate(ArchiveTransfer transfer, Long operatorId) {
        Long tenantId = resolveTenantId(transfer.getTenantId());
        transfer.setTenantId(tenantId);
        transfer.setTransferSourceEnum(CampusDomainConstants.TRANSFER_SOURCE_UPDATE_DERIVED);
        validateOrgDiff(transfer);
        if (transfer.getEffectiveDate() == null) {
            transfer.setEffectiveDate(LocalDate.now());
        }
        return persist(transfer, operatorId);
    }

    private ArchiveTransfer persist(ArchiveTransfer transfer, Long operatorId) {
        if (transfer.getSubjectId() == null) {
            throw new BusinessException("TRANSFER_SUBJECT_REQUIRED", "调动主体不能为空");
        }
        if (transfer.getTransferTypeEnum() == null) {
            throw new BusinessException("TRANSFER_TYPE_REQUIRED", "调动类型不能为空");
        }
        if (transfer.getTransferSourceEnum() == null) {
            throw new BusinessException("TRANSFER_SOURCE_REQUIRED", "调动来源不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        transfer.setOperateTime(transfer.getOperateTime() != null ? transfer.getOperateTime() : now);
        transfer.setCreatedAt(now);
        transfer.setCreatedBy(operatorId);
        transfer.setUpdatedAt(now);
        transfer.setUpdatedBy(operatorId);
        if (transfer.getOperatorId() == null) {
            transfer.setOperatorId(operatorId);
        }
        if (transfer.getVersion() == null) {
            transfer.setVersion(0);
        }
        return repository.save(transfer);
    }

    private void validateOrgDiff(ArchiveTransfer transfer) {
        boolean campusChanged = !Objects.equals(transfer.getFromCampusCode(), transfer.getToCampusCode());
        boolean collegeChanged = !Objects.equals(transfer.getFromCollegeId(), transfer.getToCollegeId());
        boolean departmentChanged = !Objects.equals(transfer.getFromDepartmentId(), transfer.getToDepartmentId());
        if (!campusChanged && !collegeChanged && !departmentChanged) {
            throw new BusinessException("TRANSFER_NO_DIFF", "调动前后组织字段无差异");
        }
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
