/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gfchargeratioitem.service;

import com.openhis.campus.domain.gfchargeratioitem.entity.GfChargeRatioItem;
import com.openhis.campus.domain.gfchargeratioitem.repository.GfChargeRatioItemRepository;
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
 * 项目级公费比例读侧。
 */
@Service
@RequiredArgsConstructor
public class GfChargeRatioItemQueryService {

    private final GfChargeRatioItemRepository repository;

    public Page<GfChargeRatioItem> page(
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

    public GfChargeRatioItem getById(Long tenantId, Long id) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || id == null) {
            throw new BusinessException("GF_RATIO_NOT_FOUND", "项目比例不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, resolvedTenantId)
            .orElseThrow(() -> new BusinessException("GF_RATIO_NOT_FOUND", "项目比例不存在"));
    }

    public Optional<GfChargeRatioItem> getCurrent(
        Long tenantId,
        String itemCode,
        String chargeModeEnum,
        LocalDateTime at) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || itemCode == null || itemCode.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndItemCodeAndChargeModeEnumAndIsDeletedFalse(
                resolvedTenantId, itemCode, chargeModeEnum)
            .filter(item -> CampusGfEffectivePeriod.isEffective(at, item.getEffectiveFrom(), item.getEffectiveTo()));
    }

    private Specification<GfChargeRatioItem> buildSpec(Long tenantId, String chargeModeEnum, String keyword) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (chargeModeEnum != null && !chargeModeEnum.isBlank()) {
                predicates.add(cb.equal(root.get("chargeModeEnum"), chargeModeEnum));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("itemCode")), pattern));
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
