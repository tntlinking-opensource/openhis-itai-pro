/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gffreepercent.service;

import com.openhis.campus.domain.gffreepercent.entity.GfFreePercent;
import com.openhis.campus.domain.gffreepercent.repository.GfFreePercentRepository;
import com.openhis.campus.domain.shared.CampusGfConstants;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 甲乙丙自付比例读侧。
 */
@Service
@RequiredArgsConstructor
public class GfFreePercentQueryService {

    private final GfFreePercentRepository repository;

    public List<GfFreePercent> listByMode(Long tenantId, String chargeModeEnum) {
        CampusGfConstants.validateChargeMode(chargeModeEnum);
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null) {
            return List.of();
        }
        return repository.findByTenantIdAndChargeModeEnumAndIsDeletedFalseOrderByClassEnumAsc(
            resolvedTenantId, chargeModeEnum);
    }

    public Optional<GfFreePercent> findByClassAndMode(Long tenantId, String classEnum, String chargeModeEnum) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || classEnum == null || classEnum.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndClassEnumAndChargeModeEnumAndIsDeletedFalse(
            resolvedTenantId, classEnum, chargeModeEnum);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
