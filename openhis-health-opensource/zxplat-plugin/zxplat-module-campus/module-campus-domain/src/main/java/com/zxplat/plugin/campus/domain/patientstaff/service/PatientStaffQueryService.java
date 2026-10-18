/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.patientstaff.service;

import com.zxplat.plugin.campus.domain.patientstaff.entity.PatientStaff;
import com.zxplat.plugin.campus.domain.patientstaff.repository.PatientStaffRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 教职工档案读侧。
 */
@Service
@RequiredArgsConstructor
public class PatientStaffQueryService {

    private final PatientStaffRepository repository;

    public Page<PatientStaff> page(
        Long tenantId,
        String keyword,
        Long departmentId,
        Long campusSiteId,
        String jobStatus,
        Boolean activeFlag,
        Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return Page.empty(pageable);
        }
        return repository.findAll(
            buildSpec(resolvedTenantId, keyword, departmentId, campusSiteId, jobStatus, activeFlag),
            pageable);
    }

    public PatientStaff getById(Long tenantId, Long id) {
        return requireStaff(resolveTenantId(tenantId), id);
    }

    public Optional<PatientStaff> findByEmployeeNo(Long tenantId, String employeeNo) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || employeeNo == null || employeeNo.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndEmployeeNoAndIsDeletedFalse(resolvedTenantId, employeeNo);
    }

    public Optional<PatientStaff> findByPatientId(Long tenantId, Long patientId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || patientId == null) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndPatientIdAndIsDeletedFalse(resolvedTenantId, patientId);
    }

    public List<PatientStaff> listOrphans(Long tenantId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return List.of();
        }
        return repository.findByTenantIdAndPatientIdIsNullAndIsDeletedFalseOrderByIdAsc(resolvedTenantId);
    }

    /**
     * 团检名册：部门下在职且已绑定患者的教职工。
     */
    public List<PatientStaff> listForRoster(Long tenantId, Long departmentId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || departmentId == null) {
            return List.of();
        }
        return repository.findAll((root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), resolvedTenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.isTrue(root.get("activeFlag")));
            predicates.add(cb.equal(root.get("departmentId"), departmentId));
            predicates.add(cb.isNotNull(root.get("patientId")));
            predicates.add(cb.equal(root.get("jobStatusEnum"),
                com.zxplat.plugin.campus.domain.shared.CampusDomainConstants.JOB_STATUS_ACTIVE));
            cq.orderBy(cb.asc(root.get("employeeNo")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    private Specification<PatientStaff> buildSpec(
        Long tenantId,
        String keyword,
        Long departmentId,
        Long campusSiteId,
        String jobStatus,
        Boolean activeFlag) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("departmentId"), departmentId));
            }
            if (campusSiteId != null) {
                predicates.add(cb.equal(root.get("campusSiteId"), campusSiteId));
            }
            if (jobStatus != null && !jobStatus.isBlank()) {
                predicates.add(cb.equal(root.get("jobStatusEnum"), jobStatus));
            }
            if (activeFlag != null) {
                predicates.add(cb.equal(root.get("activeFlag"), activeFlag));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("employeeNo")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("medicalCode")), pattern),
                    cb.like(cb.lower(root.get("idCard")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PatientStaff requireStaff(Long tenantId, Long id) {
        if (tenantId == null || id == null) {
            throw new BusinessException("STAFF_NOT_FOUND", "教职工档案不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "教职工档案不存在"));
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
