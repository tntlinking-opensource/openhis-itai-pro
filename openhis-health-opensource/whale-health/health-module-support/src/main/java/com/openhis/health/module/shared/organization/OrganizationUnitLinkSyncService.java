/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.shared.organization;

import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.whale.core.application.IOrganizationUnitScopeBridge;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.identity.application.OrganizationUnitAppService;
import com.openhis.whale.identity.domain.OrganizationUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * HIS {@code adm_organization} 与 Whale {@link OrganizationUnit} 的按需同步（岗位/登录场景）。
 */
@Service
@RequiredArgsConstructor
public class OrganizationUnitLinkSyncService {

    private static final String DEFAULT_OU_DISPLAY_NAME = "department";

    private final OrganizationQueryService organizationQueryService;
    private final OrganizationUnitAppService organizationUnitAppService;
    private final IOrganizationUnitScopeBridge organizationUnitScopeBridge;

    /**
     * 将 HIS 组织 id 列表解析为 Whale OU id；缺失时按 {@link OrganizationUnitLinkCode} 补建 OU。
     *
     * @param businessOrganizationIds HIS {@code adm_organization.id} 列表
     * @return 已存在或新建成功的 Whale OU id 列表
     */
    public List<Long> resolveWhaleOrganizationUnitIds(List<Long> businessOrganizationIds) {
        if (businessOrganizationIds == null || businessOrganizationIds.isEmpty()) {
            return List.of();
        }
        List<Long> whaleIds = new ArrayList<>();
        for (Long businessOrgId : businessOrganizationIds) {
            if (businessOrgId == null) {
                continue;
            }
            ensureWhaleOrganizationUnitId(businessOrgId).ifPresent(whaleIds::add);
        }
        return whaleIds;
    }

    /**
     * 查找或创建与 HIS 组织关联的 Whale OU。
     *
     * @param businessOrganizationId HIS 组织主键
     * @return Whale OU 主键；组织不存在或无法生成 code 时为空
     */
    public Optional<Long> ensureWhaleOrganizationUnitId(Long businessOrganizationId) {
        if (businessOrganizationId == null) {
            return Optional.empty();
        }
        Optional<Long> existing = organizationUnitScopeBridge
            .findWhaleOrganizationUnitIdByBusinessOrganizationId(businessOrganizationId);
        if (existing.isPresent()) {
            return existing;
        }
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(businessOrganizationId);
        if (organization == null) {
            return Optional.empty();
        }
        String code = OrganizationUnitLinkCode.fromOrganization(organization);
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        OrganizationUnit ou = organizationUnitAppService.findOptionalByCode(code)
            .orElseGet(() -> {
                OrganizationUnit created = new OrganizationUnit();
                created.setCode(code);
                created.setTenantId(TenantUtils.getCurrentTenantId());
                return created;
            });
        applyOrganizationToOu(organization, ou);
        restoreOuIfOrganizationActive(organization, ou);
        OrganizationUnit saved = organizationUnitAppService.persistOrganizationUnit(ou);
        if (saved == null || saved.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(saved.getId());
    }

    /**
     * 用 HIS 组织快照刷新 OU 展示字段与启用态。
     *
     * @param organization HIS 组织只读快照
     * @param ou           待更新的 Whale OU
     */
    private void applyOrganizationToOu(Organization organization, OrganizationUnit ou) {
        ou.setDisplayName(resolveOuDisplayName(organization));
        if (organization.getDisplayOrder() != null) {
            ou.setSortOrder(organization.getDisplayOrder());
        } else {
            ou.setSortOrder(0);
        }
        ou.setIsEnabled(Boolean.TRUE.equals(organization.getActiveFlag()));
    }

    /**
     * 组织仍为启用态时，恢复被逻辑删除的 OU 行。
     *
     * @param organization HIS 组织快照
     * @param ou           待恢复的 Whale OU
     */
    private void restoreOuIfOrganizationActive(Organization organization, OrganizationUnit ou) {
        if (organization == null || ou == null || !Boolean.TRUE.equals(organization.getActiveFlag())) {
            return;
        }
        ou.setIsDeleted(Boolean.FALSE);
        ou.setDeletedAt(null);
        ou.setDeletedBy(null);
    }

    /**
     * 解析 OU 展示名；组织名为空时使用占位文案。
     *
     * @param organization HIS 组织快照
     * @return 非空展示名
     */
    private String resolveOuDisplayName(Organization organization) {
        if (organization == null || organization.getName() == null || organization.getName().isBlank()) {
            return DEFAULT_OU_DISPLAY_NAME;
        }
        return organization.getName();
    }
}
