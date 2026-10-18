/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.campussite.service;

import com.zxplat.plugin.campus.domain.campussite.entity.CampusSite;
import com.zxplat.plugin.campus.domain.campussite.repository.CampusSiteRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 校区写侧。
 */
@Service
@RequiredArgsConstructor
public class CampusSiteDomainService {

    private static final Pattern CAMPUS_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{2,32}$");

    private final CampusSiteRepository repository;

    public CampusSite create(CampusSite site, Long operatorId) {
        Long tenantId = resolveTenantId(site.getTenantId());
        site.setTenantId(tenantId);
        validateCampusCode(site.getCampusCode());
        repository.findByTenantIdAndCampusCodeAndIsDeletedFalse(tenantId, site.getCampusCode())
            .ifPresent(existing -> {
                throw new BusinessException("CAMPUS_CODE_DUPLICATE", "校区编码已存在");
            });
        applyDefaults(site);
        touchAudit(site, operatorId, true);
        return repository.save(site);
    }

    public CampusSite update(CampusSite incoming, Long operatorId) {
        Long tenantId = resolveTenantId(incoming.getTenantId());
        CampusSite existing = requireSite(tenantId, incoming.getId());
        existing.setCampusName(incoming.getCampusName());
        existing.setShortName(incoming.getShortName());
        existing.setAddress(incoming.getAddress());
        existing.setSortOrder(incoming.getSortOrder() != null ? incoming.getSortOrder() : existing.getSortOrder());
        existing.setRemark(incoming.getRemark());
        touchAudit(existing, operatorId, false);
        return repository.save(existing);
    }

    public CampusSite changeActive(Long tenantId, Long id, Boolean activeFlag, Long operatorId) {
        CampusSite site = requireSite(resolveTenantId(tenantId), id);
        site.setActiveFlag(activeFlag != null ? activeFlag : Boolean.TRUE);
        touchAudit(site, operatorId, false);
        return repository.save(site);
    }

    public CampusSite setPrimary(Long tenantId, Long id, Long operatorId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        CampusSite target = requireSite(resolvedTenantId, id);
        LocalDateTime now = LocalDateTime.now();
        for (CampusSite primary : repository.findByTenantIdAndIsPrimaryTrueAndIsDeletedFalse(resolvedTenantId)) {
            if (!primary.getId().equals(id)) {
                primary.setIsPrimary(Boolean.FALSE);
                primary.setUpdatedAt(now);
                primary.setUpdatedBy(operatorId);
                repository.save(primary);
            }
        }
        target.setIsPrimary(Boolean.TRUE);
        touchAudit(target, operatorId, false);
        return repository.save(target);
    }

    private CampusSite requireSite(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("CAMPUS_NOT_FOUND", "校区不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("CAMPUS_NOT_FOUND", "校区不存在"));
    }

    private void validateCampusCode(String campusCode) {
        if (campusCode == null || !CAMPUS_CODE_PATTERN.matcher(campusCode).matches()) {
            throw new BusinessException("CAMPUS_CODE_INVALID", "校区编码格式无效");
        }
    }

    private void applyDefaults(CampusSite site) {
        if (site.getIsPrimary() == null) {
            site.setIsPrimary(Boolean.FALSE);
        }
        if (site.getActiveFlag() == null) {
            site.setActiveFlag(Boolean.TRUE);
        }
        if (site.getSortOrder() == null) {
            site.setSortOrder(0);
        }
    }

    private void touchAudit(CampusSite site, Long operatorId, boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            site.setCreatedAt(now);
            site.setCreatedBy(operatorId);
            site.setIsDeleted(Boolean.FALSE);
        }
        site.setUpdatedAt(now);
        site.setUpdatedBy(operatorId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
