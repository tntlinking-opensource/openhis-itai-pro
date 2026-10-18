/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.gfchargeratio.service;

import com.zxplat.plugin.campus.domain.gfchargeratio.entity.GfChargeRatio;
import com.zxplat.plugin.campus.domain.gfchargeratio.repository.GfChargeRatioRepository;
import com.zxplat.plugin.campus.domain.shared.CampusGfConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 目录级公费比例写侧。
 */
@Service
@RequiredArgsConstructor
public class GfChargeRatioDomainService {

    private final GfChargeRatioRepository repository;

    public GfChargeRatio upsert(GfChargeRatio incoming, Long operatorId) {
        CampusGfConstants.validateChargeMode(incoming.getChargeModeEnum());
        CampusGfConstants.validateRatio(incoming.getRatio());
        if (incoming.getCatalogCode() == null || incoming.getCatalogCode().isBlank()) {
            throw new BusinessException("GF_RATIO_OUT_OF_RANGE", "目录编码不能为空");
        }
        Long tenantId = resolveTenantId(incoming.getTenantId());
        GfChargeRatio entity;
        if (incoming.getId() != null) {
            entity = repository.findByIdAndTenantIdAndIsDeletedFalse(incoming.getId(), tenantId)
                .orElseThrow(() -> new BusinessException("GF_RATIO_NOT_FOUND", "目录比例不存在"));
        } else {
            entity = repository.findByTenantIdAndCatalogCodeAndChargeModeEnumAndIsDeletedFalse(
                    tenantId, incoming.getCatalogCode(), incoming.getChargeModeEnum())
                .orElseGet(() -> {
                    GfChargeRatio created = new GfChargeRatio();
                    created.setTenantId(tenantId);
                    created.setCatalogCode(incoming.getCatalogCode());
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

    public GfChargeRatio disable(Long tenantId, Long id, Long operatorId) {
        GfChargeRatio entity = requireRatio(resolveTenantId(tenantId), id);
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(operatorId);
        return repository.save(entity);
    }

    private GfChargeRatio requireRatio(Long tenantId, Long id) {
        if (tenantId == null || id == null) {
            throw new BusinessException("GF_RATIO_NOT_FOUND", "目录比例不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("GF_RATIO_NOT_FOUND", "目录比例不存在"));
    }

    private void touchAudit(GfChargeRatio entity, Long operatorId, boolean creating) {
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
