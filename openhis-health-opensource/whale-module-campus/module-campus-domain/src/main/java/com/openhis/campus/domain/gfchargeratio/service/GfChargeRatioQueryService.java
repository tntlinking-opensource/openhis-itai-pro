/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gfchargeratio.service;

import com.openhis.campus.domain.gfchargeratio.entity.GfChargeRatio;
import com.openhis.campus.domain.gfchargeratio.repository.GfChargeRatioRepository;
import com.openhis.campus.domain.shared.CampusGfEffectivePeriod;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 目录级公费比例读侧。
 */
@Service
@RequiredArgsConstructor
public class GfChargeRatioQueryService {

    private final GfChargeRatioRepository repository;

    public Page<GfChargeRatio> page(
        Long tenantId,
        String chargeModeEnum,
        String keyword,
        Pageable pageable) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return Page.empty(pageable);
        }
        return repository.findAll(buildSpec(resolvedTenantId, chargeModeEnum, keyword), pageable);
    }

    public GfChargeRatio getById(Long tenantId, Long id) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || id == null) {
            throw new BusinessException("GF_RATIO_NOT_FOUND", "目录比例不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, resolvedTenantId)
            .orElseThrow(() -> new BusinessException("GF_RATIO_NOT_FOUND", "目录比例不存在"));
    }

    public Optional<GfChargeRatio> findCurrent(
        Long tenantId,
        String catalogCode,
        String chargeModeEnum,
        LocalDateTime at) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || catalogCode == null || catalogCode.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndCatalogCodeAndChargeModeEnumAndIsDeletedFalse(
                resolvedTenantId, catalogCode, chargeModeEnum)
            .filter(ratio -> CampusGfEffectivePeriod.isEffective(at, ratio.getEffectiveFrom(), ratio.getEffectiveTo()));
    }

    private Specification<GfChargeRatio> buildSpec(Long tenantId, String chargeModeEnum, String keyword) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (chargeModeEnum != null && !chargeModeEnum.isBlank()) {
                predicates.add(cb.equal(root.get("chargeModeEnum"), chargeModeEnum));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("catalogCode")), pattern));
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
