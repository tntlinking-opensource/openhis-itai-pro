/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gffreepercent.service;

import com.openhis.campus.domain.gffreepercent.entity.GfFreePercent;
import com.openhis.campus.domain.gffreepercent.repository.GfFreePercentRepository;
import com.openhis.campus.domain.shared.CampusGfConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 甲乙丙自付比例写侧（CFG-03）。
 */
@Service
@RequiredArgsConstructor
public class GfFreePercentDomainService {

    private final GfFreePercentRepository repository;

    public List<GfFreePercent> saveByMode(
        Long tenantId,
        String chargeModeEnum,
        List<GfFreePercent> incoming,
        Long operatorId) {
        CampusGfConstants.validateChargeMode(chargeModeEnum);
        if (incoming == null || incoming.isEmpty()) {
            throw new BusinessException("GF_RATIO_OUT_OF_RANGE", "甲乙丙比例列表不能为空");
        }
        Long resolvedTenantId = resolveTenantId(tenantId);
        List<GfFreePercent> saved = new ArrayList<>();
        for (GfFreePercent item : incoming) {
            if (item.getClassEnum() == null || item.getClassEnum().isBlank()) {
                throw new BusinessException("GF_RATIO_OUT_OF_RANGE", "甲乙丙类别不能为空");
            }
            CampusGfConstants.validateRatio(item.getSelfPayRatio());
            GfFreePercent entity = repository
                .findByTenantIdAndClassEnumAndChargeModeEnumAndIsDeletedFalse(
                    resolvedTenantId, item.getClassEnum(), chargeModeEnum)
                .orElseGet(() -> {
                    GfFreePercent created = new GfFreePercent();
                    created.setTenantId(resolvedTenantId);
                    created.setClassEnum(item.getClassEnum());
                    created.setChargeModeEnum(chargeModeEnum);
                    created.setIsDeleted(Boolean.FALSE);
                    return created;
                });
            entity.setSelfPayRatio(item.getSelfPayRatio());
            touchAudit(entity, operatorId, entity.getId() == null);
            saved.add(repository.save(entity));
        }
        return saved;
    }

    private void touchAudit(GfFreePercent entity, Long operatorId, boolean creating) {
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
