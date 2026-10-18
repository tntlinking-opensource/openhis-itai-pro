/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.campusdepartment.service;

import com.zxplat.plugin.campus.domain.campuscollege.entity.CampusCollege;
import com.zxplat.plugin.campus.domain.campuscollege.repository.CampusCollegeRepository;
import com.zxplat.plugin.campus.domain.campusdepartment.entity.CampusDepartment;
import com.zxplat.plugin.campus.domain.campusdepartment.repository.CampusDepartmentRepository;
import com.zxplat.plugin.campus.domain.campussite.entity.CampusSite;
import com.zxplat.plugin.campus.domain.campussite.repository.CampusSiteRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 部门写侧。
 */
@Service
@RequiredArgsConstructor
public class CampusDepartmentDomainService {

    private final CampusDepartmentRepository repository;
    private final CampusSiteRepository campusSiteRepository;
    private final CampusCollegeRepository collegeRepository;

    public CampusDepartment create(CampusDepartment department, Long operatorId) {
        Long tenantId = resolveTenantId(department.getTenantId());
        department.setTenantId(tenantId);
        validateCodeUnique(tenantId, department.getDepartmentCode(), null);
        validateCampusSite(tenantId, department.getCampusSiteId());
        validateCollegeBinding(tenantId, department.getCampusSiteId(), department.getCollegeId());
        applyDefaults(department);
        touchAudit(department, operatorId, true);
        return repository.save(department);
    }

    public CampusDepartment update(CampusDepartment incoming, Long operatorId) {
        Long tenantId = resolveTenantId(incoming.getTenantId());
        CampusDepartment existing = requireDepartment(tenantId, incoming.getId());
        existing.setDepartmentName(incoming.getDepartmentName());
        existing.setDeptTypeEnum(incoming.getDeptTypeEnum());
        existing.setSortOrder(incoming.getSortOrder() != null ? incoming.getSortOrder() : existing.getSortOrder());
        existing.setRemark(incoming.getRemark());
        touchAudit(existing, operatorId, false);
        return repository.save(existing);
    }

    public CampusDepartment changeActive(Long tenantId, Long id, Boolean activeFlag, Long operatorId) {
        CampusDepartment department = requireDepartment(resolveTenantId(tenantId), id);
        department.setActiveFlag(activeFlag != null ? activeFlag : Boolean.TRUE);
        touchAudit(department, operatorId, false);
        return repository.save(department);
    }

    public CampusDepartment reparent(
        Long tenantId,
        Long id,
        Long campusSiteId,
        Long collegeId,
        Long operatorId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        CampusDepartment department = requireDepartment(resolvedTenantId, id);
        validateCampusSite(resolvedTenantId, campusSiteId);
        validateCollegeBinding(resolvedTenantId, campusSiteId, collegeId);
        department.setCampusSiteId(campusSiteId);
        department.setCollegeId(collegeId);
        touchAudit(department, operatorId, false);
        return repository.save(department);
    }

    private void validateCodeUnique(Long tenantId, String departmentCode, Long excludeId) {
        repository.findByTenantIdAndDepartmentCodeAndIsDeletedFalse(tenantId, departmentCode).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw new BusinessException("DEPT_CODE_DUPLICATE", "部门编码已存在");
            }
        });
    }

    private void validateCampusSite(Long tenantId, Long campusSiteId) {
        if (campusSiteId == null) {
            throw new BusinessException("CAMPUS_NOT_FOUND", "校区不存在");
        }
        CampusSite site = campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(campusSiteId, tenantId)
            .orElseThrow(() -> new BusinessException("CAMPUS_NOT_FOUND", "校区不存在"));
        if (!Boolean.TRUE.equals(site.getActiveFlag())) {
            throw new BusinessException("CAMPUS_INACTIVE", "校区未启用");
        }
    }

    private void validateCollegeBinding(Long tenantId, Long campusSiteId, Long collegeId) {
        if (collegeId == null) {
            return;
        }
        CampusCollege college = collegeRepository.findByIdAndTenantIdAndIsDeletedFalse(collegeId, tenantId)
            .orElseThrow(() -> new BusinessException("COLLEGE_NOT_FOUND", "学院不存在"));
        if (college.getCampusSiteId() != null && !college.getCampusSiteId().equals(campusSiteId)) {
            throw new BusinessException("COLLEGE_CAMPUS_MISMATCH", "学院与校区归属不一致");
        }
    }

    private CampusDepartment requireDepartment(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("DEPT_NOT_FOUND", "部门不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("DEPT_NOT_FOUND", "部门不存在"));
    }

    private void applyDefaults(CampusDepartment department) {
        if (department.getActiveFlag() == null) {
            department.setActiveFlag(Boolean.TRUE);
        }
        if (department.getSortOrder() == null) {
            department.setSortOrder(0);
        }
    }

    private void touchAudit(CampusDepartment department, Long operatorId, boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            department.setCreatedAt(now);
            department.setCreatedBy(operatorId);
            department.setIsDeleted(Boolean.FALSE);
        }
        department.setUpdatedAt(now);
        department.setUpdatedBy(operatorId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
