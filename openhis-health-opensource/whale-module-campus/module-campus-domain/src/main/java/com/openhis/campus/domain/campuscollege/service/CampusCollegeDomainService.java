/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.campuscollege.service;

import com.openhis.campus.domain.campuscollege.entity.CampusCollege;
import com.openhis.campus.domain.campuscollege.repository.CampusCollegeRepository;
import com.openhis.campus.domain.campussite.entity.CampusSite;
import com.openhis.campus.domain.campussite.repository.CampusSiteRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 学院写侧。
 */
@Service
@RequiredArgsConstructor
public class CampusCollegeDomainService {

    private final CampusCollegeRepository repository;
    private final CampusSiteRepository campusSiteRepository;

    public CampusCollege create(CampusCollege college, Long operatorId) {
        Long tenantId = resolveTenantId(college.getTenantId());
        college.setTenantId(tenantId);
        validateCodeUnique(tenantId, college.getCollegeCode(), null);
        validateCampusBinding(tenantId, college.getCampusSiteId());
        applyDefaults(college);
        touchAudit(college, operatorId, true);
        return repository.save(college);
    }

    public CampusCollege update(CampusCollege incoming, Long operatorId) {
        Long tenantId = resolveTenantId(incoming.getTenantId());
        CampusCollege existing = requireCollege(tenantId, incoming.getId());
        existing.setCollegeName(incoming.getCollegeName());
        existing.setExternalCollegeId(incoming.getExternalCollegeId());
        existing.setSortOrder(incoming.getSortOrder() != null ? incoming.getSortOrder() : existing.getSortOrder());
        existing.setRemark(incoming.getRemark());
        touchAudit(existing, operatorId, false);
        return repository.save(existing);
    }

    public CampusCollege changeActive(Long tenantId, Long id, Boolean activeFlag, Long operatorId) {
        CampusCollege college = requireCollege(resolveTenantId(tenantId), id);
        college.setActiveFlag(activeFlag != null ? activeFlag : Boolean.TRUE);
        touchAudit(college, operatorId, false);
        return repository.save(college);
    }

    public CampusCollege bindCampus(Long tenantId, Long id, Long campusSiteId, Long operatorId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        CampusCollege college = requireCollege(resolvedTenantId, id);
        if (campusSiteId != null) {
            validateCampusBinding(resolvedTenantId, campusSiteId);
        }
        college.setCampusSiteId(campusSiteId);
        touchAudit(college, operatorId, false);
        return repository.save(college);
    }

    private void validateCodeUnique(Long tenantId, String collegeCode, Long excludeId) {
        repository.findByTenantIdAndCollegeCodeAndIsDeletedFalse(tenantId, collegeCode).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw new BusinessException("COLLEGE_CODE_DUPLICATE", "学院编码已存在");
            }
        });
    }

    private void validateCampusBinding(Long tenantId, Long campusSiteId) {
        CampusSite site = campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(campusSiteId, tenantId)
            .orElseThrow(() -> new BusinessException("CAMPUS_NOT_FOUND", "校区不存在"));
        if (!Boolean.TRUE.equals(site.getActiveFlag())) {
            throw new BusinessException("CAMPUS_INACTIVE", "校区未启用");
        }
    }

    private CampusCollege requireCollege(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("COLLEGE_NOT_FOUND", "学院不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("COLLEGE_NOT_FOUND", "学院不存在"));
    }

    private void applyDefaults(CampusCollege college) {
        if (college.getActiveFlag() == null) {
            college.setActiveFlag(Boolean.TRUE);
        }
        if (college.getSortOrder() == null) {
            college.setSortOrder(0);
        }
    }

    private void touchAudit(CampusCollege college, Long operatorId, boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            college.setCreatedAt(now);
            college.setCreatedBy(operatorId);
            college.setIsDeleted(Boolean.FALSE);
        }
        college.setUpdatedAt(now);
        college.setUpdatedBy(operatorId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
