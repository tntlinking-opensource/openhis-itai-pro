/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.publicexpenseprofile.service;

import com.openhis.campus.domain.publicexpenseprofile.entity.PublicExpenseProfile;
import com.openhis.campus.domain.publicexpenseprofile.repository.PublicExpenseProfileRepository;
import com.openhis.campus.domain.shared.CampusDomainConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 公费资格档案写侧。
 */
@Service
@RequiredArgsConstructor
public class PublicExpenseProfileDomainService {

    private final PublicExpenseProfileRepository repository;

    public PublicExpenseProfile upsert(PublicExpenseProfile profile, Long operatorId) {
        Long tenantId = resolveTenantId(profile.getTenantId());
        profile.setTenantId(tenantId);
        PublicExpenseProfile existing = repository
            .findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(
                tenantId, profile.getSubjectTypeEnum(), profile.getSubjectId())
            .orElse(null);
        if (existing == null) {
            applyDefaults(profile);
            touchAudit(profile, operatorId, true);
            return repository.save(profile);
        }
        existing.setPatientId(profile.getPatientId());
        existing.setCardNo(profile.getCardNo());
        existing.setMedicalCode(profile.getMedicalCode());
        existing.setGfStatusEnum(
            profile.getGfStatusEnum() != null ? profile.getGfStatusEnum() : existing.getGfStatusEnum());
        existing.setCategoryEnum(profile.getCategoryEnum());
        existing.setValidFrom(profile.getValidFrom());
        existing.setValidTo(profile.getValidTo());
        if (profile.getEligibleFlag() != null) {
            existing.setEligibleFlag(profile.getEligibleFlag());
        }
        existing.setRemark(profile.getRemark());
        touchAudit(existing, operatorId, false);
        return repository.save(existing);
    }

    public PublicExpenseProfile syncFromHis(
        Long tenantId,
        String subjectTypeEnum,
        Long subjectId,
        String cardNo,
        String gfStatus,
        Long operatorId) {
        PublicExpenseProfile profile = repository
            .findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(
                resolveTenantId(tenantId), subjectTypeEnum, subjectId)
            .orElseGet(() -> {
                PublicExpenseProfile created = new PublicExpenseProfile();
                created.setTenantId(resolveTenantId(tenantId));
                created.setSubjectTypeEnum(subjectTypeEnum);
                created.setSubjectId(subjectId);
                applyDefaults(created);
                return created;
            });
        profile.setCardNo(cardNo);
        profile.setGfStatusEnum(mapHisGfStatus(gfStatus));
        if (profile.getId() == null) {
            touchAudit(profile, operatorId, true);
        } else {
            touchAudit(profile, operatorId, false);
        }
        return repository.save(profile);
    }

    public PublicExpenseProfile disable(Long tenantId, String subjectTypeEnum, Long subjectId, Long operatorId) {
        PublicExpenseProfile profile = requireProfile(resolveTenantId(tenantId), subjectTypeEnum, subjectId);
        profile.setEligibleFlag(Boolean.FALSE);
        profile.setIsDeleted(Boolean.TRUE);
        profile.setDeletedAt(LocalDateTime.now());
        profile.setDeletedBy(operatorId);
        touchAudit(profile, operatorId, false);
        return repository.save(profile);
    }

    private PublicExpenseProfile requireProfile(Long tenantId, String subjectTypeEnum, Long subjectId) {
        return repository.findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(
                tenantId, subjectTypeEnum, subjectId)
            .orElseThrow(() -> new BusinessException("PE_PROFILE_NOT_FOUND", "公费资格档案不存在"));
    }

    private String mapHisGfStatus(String gfStatus) {
        if (gfStatus == null || gfStatus.isBlank()) {
            return CampusDomainConstants.GF_STATUS_UNKNOWN;
        }
        return switch (gfStatus.trim()) {
            case "0", "NOT_ELIGIBLE" -> CampusDomainConstants.GF_STATUS_NOT_ELIGIBLE;
            case "1", "ELIGIBLE" -> CampusDomainConstants.GF_STATUS_ELIGIBLE;
            case "2", "UNKNOWN" -> CampusDomainConstants.GF_STATUS_UNKNOWN;
            default -> gfStatus.trim();
        };
    }

    private void applyDefaults(PublicExpenseProfile profile) {
        if (profile.getGfStatusEnum() == null) {
            profile.setGfStatusEnum(CampusDomainConstants.GF_STATUS_UNKNOWN);
        }
        if (profile.getEligibleFlag() == null) {
            profile.setEligibleFlag(Boolean.TRUE);
        }
    }

    private void touchAudit(PublicExpenseProfile profile, Long operatorId, boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            profile.setCreatedAt(now);
            profile.setCreatedBy(operatorId);
            profile.setIsDeleted(Boolean.FALSE);
        }
        profile.setUpdatedAt(now);
        profile.setUpdatedBy(operatorId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
