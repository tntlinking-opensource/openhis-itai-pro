/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gfratiorequest.service;

import com.openhis.campus.domain.gfratiorequest.entity.GfRatioRequest;
import com.openhis.campus.domain.gfratiorequest.repository.GfRatioRequestRepository;
import com.openhis.campus.domain.shared.CampusGfConstants;
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
 * 公费比例调整申请读侧。
 */
@Service
@RequiredArgsConstructor
public class GfRatioRequestQueryService {

    private final GfRatioRequestRepository repository;

    public Page<GfRatioRequest> pageTodo(
        Long tenantId,
        String chargeModeEnum,
        String keyword,
        Pageable pageable) {
        return pageByStatus(tenantId, chargeModeEnum, keyword, CampusGfConstants.REQUEST_STATUS_PENDING, pageable);
    }

    public Page<GfRatioRequest> pageFinished(
        Long tenantId,
        String chargeModeEnum,
        String keyword,
        Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return Page.empty(pageable);
        }
        return repository.findAll(buildFinishedSpec(resolvedTenantId, chargeModeEnum, keyword), pageable);
    }

    public Page<GfRatioRequest> pageRequests(
        Long tenantId,
        String chargeModeEnum,
        String statusEnum,
        String keyword,
        Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return Page.empty(pageable);
        }
        return repository.findAll(buildSpec(resolvedTenantId, chargeModeEnum, statusEnum, keyword), pageable);
    }

    public GfRatioRequest getById(Long tenantId, Long id) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || id == null) {
            throw new BusinessException("GF_REQUEST_NOT_FOUND", "申请单不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, resolvedTenantId)
            .orElseThrow(() -> new BusinessException("GF_REQUEST_NOT_FOUND", "申请单不存在"));
    }

    private Page<GfRatioRequest> pageByStatus(
        Long tenantId,
        String chargeModeEnum,
        String keyword,
        String statusEnum,
        Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return Page.empty(pageable);
        }
        return repository.findAll(buildSpec(resolvedTenantId, chargeModeEnum, statusEnum, keyword), pageable);
    }

    private Specification<GfRatioRequest> buildFinishedSpec(
        Long tenantId,
        String chargeModeEnum,
        String keyword) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(root.get("statusEnum").in(
                CampusGfConstants.REQUEST_STATUS_APPROVED,
                CampusGfConstants.REQUEST_STATUS_REJECTED));
            if (chargeModeEnum != null && !chargeModeEnum.isBlank()) {
                predicates.add(cb.equal(root.get("chargeModeEnum"), chargeModeEnum));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("requestNo")), pattern),
                    cb.like(cb.lower(root.get("itemCode")), pattern),
                    cb.like(cb.lower(root.get("applicantName")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<GfRatioRequest> buildSpec(
        Long tenantId,
        String chargeModeEnum,
        String statusEnum,
        String keyword) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (chargeModeEnum != null && !chargeModeEnum.isBlank()) {
                predicates.add(cb.equal(root.get("chargeModeEnum"), chargeModeEnum));
            }
            if (statusEnum != null && !statusEnum.isBlank()) {
                predicates.add(cb.equal(root.get("statusEnum"), statusEnum));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("requestNo")), pattern),
                    cb.like(cb.lower(root.get("itemCode")), pattern),
                    cb.like(cb.lower(root.get("applicantName")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
