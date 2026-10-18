/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.gfswitch.service;

import com.zxplat.plugin.campus.domain.gfswitch.entity.GfSwitch;
import com.zxplat.plugin.campus.domain.gfswitch.repository.GfSwitchRepository;
import com.zxplat.plugin.campus.domain.shared.CampusDomainConstants;
import com.zxplat.plugin.campus.domain.shared.CampusGfConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 公费开关写侧（CFG-01 / CFG-02 只读）。
 */
@Service
@RequiredArgsConstructor
public class GfSwitchDomainService {

    private final GfSwitchRepository repository;

    public boolean getMasterSwitch(Long tenantId) {
        return repository.findByTenantIdAndSwitchKeyAndIsDeletedFalse(
                resolveTenantId(tenantId), CampusDomainConstants.GF_SWITCH_KEY_MASTER)
            .map(sw -> isTruthySwitchValue(sw.getSwitchValue()))
            .orElse(true);
    }

    public GfSwitch saveMasterSwitch(Long tenantId, boolean enabled, Long operatorId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        GfSwitch entity = repository.findByTenantIdAndSwitchKeyAndIsDeletedFalse(
                resolvedTenantId, CampusDomainConstants.GF_SWITCH_KEY_MASTER)
            .orElseGet(() -> {
                GfSwitch created = new GfSwitch();
                created.setTenantId(resolvedTenantId);
                created.setSwitchKey(CampusDomainConstants.GF_SWITCH_KEY_MASTER);
                created.setIsDeleted(Boolean.FALSE);
                return created;
            });
        entity.setSwitchValue(enabled ? "true" : "false");
        touchAudit(entity, operatorId, entity.getId() == null);
        return repository.save(entity);
    }

    public Optional<String> getCalcMode(Long tenantId) {
        return repository.findByTenantIdAndSwitchKeyAndIsDeletedFalse(
                resolveTenantId(tenantId), CampusGfConstants.GF_SWITCH_KEY_CALC_MODE)
            .map(GfSwitch::getSwitchValue)
            .filter(value -> value != null && !value.isBlank());
    }

    private boolean isTruthySwitchValue(String switchValue) {
        if (switchValue == null || switchValue.isBlank()) {
            return true;
        }
        String normalized = switchValue.trim().toLowerCase(Locale.ROOT);
        return !("false".equals(normalized) || "0".equals(normalized)
            || "off".equals(normalized) || "n".equals(normalized));
    }

    private void touchAudit(GfSwitch entity, Long operatorId, boolean creating) {
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
        Long current = TenantUtils.getCurrentTenantId();
        if (current == null) {
            throw new BusinessException("GF_SWITCH_INVALID", "租户上下文缺失");
        }
        return current;
    }
}
