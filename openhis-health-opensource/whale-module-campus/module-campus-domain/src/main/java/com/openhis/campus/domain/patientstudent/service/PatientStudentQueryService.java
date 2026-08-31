/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.patientstudent.service;

import com.openhis.campus.domain.patientstudent.entity.PatientStudent;
import com.openhis.campus.domain.patientstudent.repository.PatientStudentRepository;
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
import java.util.Optional;

/**
 * 学生档案读侧。
 */
@Service
@RequiredArgsConstructor
public class PatientStudentQueryService {

    private final PatientStudentRepository repository;

    public Page<PatientStudent> page(
        Long tenantId,
        String keyword,
        Long collegeId,
        String campusCode,
        String schoolStatus,
        Boolean activeFlag,
        String className,
        Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return Page.empty(pageable);
        }
        return repository.findAll(
            buildSpec(resolvedTenantId, keyword, collegeId, campusCode, schoolStatus, activeFlag, className),
            pageable);
    }

    public PatientStudent getById(Long tenantId, Long id) {
        return requireStudent(resolveTenantId(tenantId), id);
    }

    public Optional<PatientStudent> findByStudentNo(Long tenantId, String studentNo) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || studentNo == null || studentNo.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndStudentNoAndIsDeletedFalse(resolvedTenantId, studentNo);
    }

    public Optional<PatientStudent> findByPatientId(Long tenantId, Long patientId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || patientId == null) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndPatientIdAndIsDeletedFalse(resolvedTenantId, patientId);
    }

    public List<PatientStudent> listOrphans(Long tenantId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return List.of();
        }
        return repository.findByTenantIdAndPatientIdIsNullAndIsDeletedFalseOrderByIdAsc(resolvedTenantId);
    }

    /**
     * 团检名册：学院 + 可选班级，仅返回已绑定 patientId 的在校学生。
     */
    public List<PatientStudent> listForRoster(Long tenantId, Long collegeId, String className) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || collegeId == null) {
            return List.of();
        }
        return repository.findAll((root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), resolvedTenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.isTrue(root.get("activeFlag")));
            predicates.add(cb.equal(root.get("collegeId"), collegeId));
            predicates.add(cb.isNotNull(root.get("patientId")));
            predicates.add(cb.equal(root.get("schoolStatusEnum"),
                com.openhis.campus.domain.shared.CampusDomainConstants.SCHOOL_STATUS_IN_SCHOOL));
            if (className != null && !className.isBlank()) {
                predicates.add(cb.equal(root.get("className"), className.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<String> listDistinctClassNames(Long tenantId, Long collegeId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || collegeId == null) {
            return List.of();
        }
        return repository.findAll((root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), resolvedTenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.isTrue(root.get("activeFlag")));
            predicates.add(cb.equal(root.get("collegeId"), collegeId));
            predicates.add(cb.isNotNull(root.get("patientId")));
            predicates.add(cb.isNotNull(root.get("className")));
            predicates.add(cb.equal(root.get("schoolStatusEnum"),
                com.openhis.campus.domain.shared.CampusDomainConstants.SCHOOL_STATUS_IN_SCHOOL));
            cq.orderBy(cb.asc(root.get("className")));
            return cb.and(predicates.toArray(new Predicate[0]));
        }).stream()
            .map(PatientStudent::getClassName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .sorted()
            .toList();
    }

    private Specification<PatientStudent> buildSpec(
        Long tenantId,
        String keyword,
        Long collegeId,
        String campusCode,
        String schoolStatus,
        Boolean activeFlag,
        String className) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (collegeId != null) {
                predicates.add(cb.equal(root.get("collegeId"), collegeId));
            }
            if (campusCode != null && !campusCode.isBlank()) {
                predicates.add(cb.equal(root.get("campusCode"), campusCode));
            }
            if (schoolStatus != null && !schoolStatus.isBlank()) {
                predicates.add(cb.equal(root.get("schoolStatusEnum"), schoolStatus));
            }
            if (activeFlag != null) {
                predicates.add(cb.equal(root.get("activeFlag"), activeFlag));
            }
            if (className != null && !className.isBlank()) {
                predicates.add(cb.equal(root.get("className"), className.trim()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("studentNo")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("idCard")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PatientStudent requireStudent(Long tenantId, Long id) {
        if (tenantId == null || id == null) {
            throw new BusinessException("STUDENT_NOT_FOUND", "学生档案不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "学生档案不存在"));
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
