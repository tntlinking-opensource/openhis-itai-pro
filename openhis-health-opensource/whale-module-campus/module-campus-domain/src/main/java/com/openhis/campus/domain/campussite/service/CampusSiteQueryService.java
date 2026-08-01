/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.campussite.service;

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
import java.util.List;

/**
 * 校区读侧。
 */
@Service
@RequiredArgsConstructor
public class CampusSiteQueryService {

    private final CampusSiteRepository repository;

    public Page<CampusSite> page(Long tenantId, String keyword, Boolean activeFlag, Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return Page.empty(pageable);
        }
        return repository.findAll(buildSpec(resolvedTenantId, keyword, activeFlag), pageable);
    }

    public CampusSite getById(Long tenantId, Long id) {
        return requireSite(resolveTenantId(tenantId), id);
    }

    public CampusSite getByCode(Long tenantId, String campusCode) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || campusCode == null || campusCode.isBlank()) {
            throw new BusinessException("CAMPUS_NOT_FOUND", "校区不存在");
        }
        return repository.findByTenantIdAndCampusCodeAndIsDeletedFalse(resolvedTenantId, campusCode)
            .orElseThrow(() -> new BusinessException("CAMPUS_NOT_FOUND", "校区不存在"));
    }

    public List<CampusSite> listActive(Long tenantId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return List.of();
        }
        return repository.findByTenantIdAndActiveFlagTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc(resolvedTenantId);
    }

    private Specification<CampusSite> buildSpec(Long tenantId, String keyword, Boolean activeFlag) {
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
                    cb.like(cb.lower(root.get("campusCode")), pattern),
                    cb.like(cb.lower(root.get("campusName")), pattern),
                    cb.like(cb.lower(root.get("shortName")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private CampusSite requireSite(Long tenantId, Long id) {
        if (tenantId == null || id == null) {
            throw new BusinessException("CAMPUS_NOT_FOUND", "校区不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("CAMPUS_NOT_FOUND", "校区不存在"));
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
