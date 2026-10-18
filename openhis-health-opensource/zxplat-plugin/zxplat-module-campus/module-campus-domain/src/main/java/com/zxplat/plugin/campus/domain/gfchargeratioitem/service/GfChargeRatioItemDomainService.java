/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.gfchargeratioitem.service;

import com.zxplat.plugin.campus.domain.gfchargeratioitem.entity.GfChargeRatioItem;
import com.zxplat.plugin.campus.domain.gfchargeratioitem.repository.GfChargeRatioItemRepository;
import com.zxplat.plugin.campus.domain.shared.CampusGfConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目级公费比例写侧。
 */
@Service
@RequiredArgsConstructor
public class GfChargeRatioItemDomainService {

    private final GfChargeRatioItemRepository repository;

    public GfChargeRatioItem upsert(GfChargeRatioItem incoming, Long operatorId) {
        CampusGfConstants.validateChargeMode(incoming.getChargeModeEnum());
        CampusGfConstants.validateRatio(incoming.getRatio());
        if (incoming.getItemCode() == null || incoming.getItemCode().isBlank()) {
            throw new BusinessException("ITEM_CODE_REQUIRED", "项目编码不能为空");
        }
        Long tenantId = resolveTenantId(incoming.getTenantId());
        GfChargeRatioItem entity;
        if (incoming.getId() != null) {
            entity = repository.findByIdAndTenantIdAndIsDeletedFalse(incoming.getId(), tenantId)
                .orElseThrow(() -> new BusinessException("GF_RATIO_NOT_FOUND", "项目比例不存在"));
        } else {
            entity = repository.findByTenantIdAndItemCodeAndChargeModeEnumAndIsDeletedFalse(
                    tenantId, incoming.getItemCode(), incoming.getChargeModeEnum())
                .orElseGet(() -> {
                    GfChargeRatioItem created = new GfChargeRatioItem();
                    created.setTenantId(tenantId);
                    created.setItemCode(incoming.getItemCode());
                    created.setChargeModeEnum(incoming.getChargeModeEnum());
                    created.setIsDeleted(Boolean.FALSE);
                    return created;
                });
        }
        entity.setRatio(incoming.getRatio());
        entity.setEffectiveFrom(incoming.getEffectiveFrom());
        entity.setEffectiveTo(incoming.getEffectiveTo());
        touchAudit(entity, operatorId, entity.getId() == null);
        return repository.save(entity);
    }

    public GfChargeRatioItem applyApproved(
        Long tenantId,
        String itemCode,
        String chargeModeEnum,
        BigDecimal ratio,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Long operatorId) {
        GfChargeRatioItem incoming = new GfChargeRatioItem();
        incoming.setTenantId(resolveTenantId(tenantId));
        incoming.setItemCode(itemCode);
        incoming.setChargeModeEnum(chargeModeEnum);
        incoming.setRatio(ratio);
        incoming.setEffectiveFrom(effectiveFrom);
        incoming.setEffectiveTo(effectiveTo);
        return upsert(incoming, operatorId);
    }

    public GfChargeRatioItem disable(Long tenantId, Long id, Long operatorId) {
        GfChargeRatioItem entity = requireItem(resolveTenantId(tenantId), id);
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(operatorId);
        return repository.save(entity);
    }

    private GfChargeRatioItem requireItem(Long tenantId, Long id) {
        if (tenantId == null || id == null) {
            throw new BusinessException("GF_RATIO_NOT_FOUND", "项目比例不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("GF_RATIO_NOT_FOUND", "项目比例不存在"));
    }

    private void touchAudit(GfChargeRatioItem entity, Long operatorId, boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
