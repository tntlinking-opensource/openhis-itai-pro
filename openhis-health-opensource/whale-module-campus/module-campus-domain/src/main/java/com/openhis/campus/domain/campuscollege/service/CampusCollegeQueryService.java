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
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 学院读侧。
 */
@Service
@RequiredArgsConstructor
public class CampusCollegeQueryService {

    private final CampusCollegeRepository repository;
    private final CampusSiteRepository campusSiteRepository;

    public Page<CampusCollege> page(Long tenantId, String keyword, Boolean activeFlag, Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return Page.empty(pageable);
        }
        return repository.findAll(buildSpec(resolvedTenantId, keyword, activeFlag), pageable);
    }

    public CampusCollege getById(Long tenantId, Long id) {
        return requireCollege(resolveTenantId(tenantId), id);
    }

    public List<CampusCollege> listActive(Long tenantId, String campusCode) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return List.of();
        }
        if (campusCode == null || campusCode.isBlank()) {
            return repository.findByTenantIdAndActiveFlagTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc(resolvedTenantId);
        }
        CampusSite site = campusSiteRepository.findByTenantIdAndCampusCodeAndIsDeletedFalse(resolvedTenantId, campusCode)
            .orElse(null);
        if (site == null) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<CampusCollege> result = new ArrayList<>();
        appendColleges(result, seen,
            repository.findByTenantIdAndActiveFlagTrueAndCampusSiteIdIsNullAndIsDeletedFalseOrderBySortOrderAscIdAsc(
                resolvedTenantId));
        appendColleges(result, seen,
            repository.findByTenantIdAndActiveFlagTrueAndCampusSiteIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
                resolvedTenantId, site.getId()));
        return result;
    }

    private void appendColleges(List<CampusCollege> target, Set<Long> seen, List<CampusCollege> source) {
        for (CampusCollege college : source) {
            if (seen.add(college.getId())) {
                target.add(college);
            }
        }
    }

    private Specification<CampusCollege> buildSpec(Long tenantId, String keyword, Boolean activeFlag) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (activeFlag != null) {
                predicates.add(cb.equal(root.get("activeFlag"), activeFlag));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("collegeCode")), pattern),
                    cb.like(cb.lower(root.get("collegeName")), pattern),
                    cb.like(cb.lower(root.get("externalCollegeId")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private CampusCollege requireCollege(Long tenantId, Long id) {
        if (tenantId == null || id == null) {
            throw new BusinessException("COLLEGE_NOT_FOUND", "学院不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("COLLEGE_NOT_FOUND", "学院不存在"));
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
