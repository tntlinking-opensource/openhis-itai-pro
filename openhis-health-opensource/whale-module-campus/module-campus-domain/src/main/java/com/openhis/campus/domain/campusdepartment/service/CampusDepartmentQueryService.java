/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.campusdepartment.service;

import com.openhis.campus.domain.campusdepartment.entity.CampusDepartment;
import com.openhis.campus.domain.campusdepartment.repository.CampusDepartmentRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门读侧。
 */
@Service
@RequiredArgsConstructor
public class CampusDepartmentQueryService {

    private final CampusDepartmentRepository repository;

    public Page<CampusDepartment> page(
        Long tenantId,
        String keyword,
        Boolean activeFlag,
        Long campusSiteId,
        Long collegeId,
        Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return Page.empty(pageable);
        }
        return repository.findAll(
            buildSpec(resolvedTenantId, keyword, activeFlag, campusSiteId, collegeId),
            pageable);
    }

    public CampusDepartment getById(Long tenantId, Long id) {
        return requireDepartment(resolveTenantId(tenantId), id);
    }

    public List<CampusDepartment> list(Long tenantId, Long campusSiteId, Long collegeId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return List.of();
        }
        if (collegeId != null) {
            return repository.findByTenantIdAndCollegeIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
                resolvedTenantId, collegeId);
        }
        if (campusSiteId != null) {
            return repository.findByTenantIdAndCampusSiteIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
                resolvedTenantId, campusSiteId);
        }
        return repository.findByTenantIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(resolvedTenantId);
    }

    public List<CampusDepartment> listActive(Long tenantId, Long campusSiteId, Long collegeId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return List.of();
        }
        if (collegeId != null) {
            return repository.findByTenantIdAndActiveFlagTrueAndCollegeIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
                resolvedTenantId, collegeId);
        }
        if (campusSiteId != null) {
            return repository.findByTenantIdAndActiveFlagTrueAndCampusSiteIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
                resolvedTenantId, campusSiteId);
        }
        return repository.findByTenantIdAndActiveFlagTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc(resolvedTenantId);
    }

    private Specification<CampusDepartment> buildSpec(
        Long tenantId,
        String keyword,
        Boolean activeFlag,
        Long campusSiteId,
        Long collegeId) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (activeFlag != null) {
                predicates.add(cb.equal(root.get("activeFlag"), activeFlag));
            }
            if (campusSiteId != null) {
                predicates.add(cb.equal(root.get("campusSiteId"), campusSiteId));
            }
            if (collegeId != null) {
                predicates.add(cb.equal(root.get("collegeId"), collegeId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("departmentCode")), pattern),
                    cb.like(cb.lower(root.get("departmentName")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private CampusDepartment requireDepartment(Long tenantId, Long id) {
        if (tenantId == null || id == null) {
            throw new BusinessException("DEPT_NOT_FOUND", "部门不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("DEPT_NOT_FOUND", "部门不存在"));
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
