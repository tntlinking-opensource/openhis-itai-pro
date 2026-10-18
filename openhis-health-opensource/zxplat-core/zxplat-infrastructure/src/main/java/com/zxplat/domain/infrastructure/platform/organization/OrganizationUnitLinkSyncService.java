/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.platform.organization;

import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.framework.core.application.IOrganizationUnitScopeBridge;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.identity.application.OrganizationUnitAppService;
import com.zxplat.framework.identity.domain.OrganizationUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HIS {@code adm_organization} 与 ZXPlat {@link OrganizationUnit} 的按需同步（岗位/登录场景）。
 *
 * <p>批量解析优先：一次加载机构 + 一次按 code 查 OU，避免登录选科室时按科室 N+1。</p>
 */
@Service
@RequiredArgsConstructor
public class OrganizationUnitLinkSyncService {

    private static final String DEFAULT_OU_DISPLAY_NAME = "department";

    private final OrganizationQueryService organizationQueryService;
    private final OrganizationUnitAppService organizationUnitAppService;
    private final IOrganizationUnitScopeBridge organizationUnitScopeBridge;

    /**
     * 将 HIS 组织 id 列表解析为 ZXPlat OU id；缺失时按 {@link OrganizationUnitLinkCode} 补建 OU。
     *
     * <p>热路径（登录 sync）：批量读机构与 OU；仅对尚未建 OU 的科室回落单条补建。</p>
     *
     * @param businessOrganizationIds HIS {@code adm_organization.id} 列表
     * @return 已存在或新建成功的 ZXPlat OU id 列表（去重、保持入参顺序）
     */
    public List<Long> resolveOrganizationUnitIds(List<Long> businessOrganizationIds) {
        if (businessOrganizationIds == null || businessOrganizationIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinctBusinessIds = new LinkedHashSet<>();
        for (Long businessOrgId : businessOrganizationIds) {
            if (businessOrgId != null) {
                distinctBusinessIds.add(businessOrgId);
            }
        }
        if (distinctBusinessIds.isEmpty()) {
            return List.of();
        }

        // Pre：一次查出全部 HIS 机构，再生成 code 列表
        Map<Long, Organization> organizationById = organizationQueryService.mapByIds(distinctBusinessIds);
        List<String> codes = new ArrayList<>(organizationById.size());
        for (Long businessOrgId : distinctBusinessIds) {
            Organization organization = organizationById.get(businessOrgId);
            if (organization == null) {
                continue;
            }
            String code = OrganizationUnitLinkCode.fromOrganization(organization);
            if (code == null || code.isBlank()) {
                continue;
            }
            codes.add(code);
        }
        // Pre：一次按 code 命中已有 ZXPlat OU
        Map<String, OrganizationUnit> unitByCode = organizationUnitAppService.mapByCodes(codes);

        List<Long> zxplatOuIds = new ArrayList<>(distinctBusinessIds.size());
        for (Long businessOrgId : distinctBusinessIds) {
            Organization organization = organizationById.get(businessOrgId);
            // 机构不存在或越租户：跳过，不阻断其余科室登录
            if (organization == null) {
                continue;
            }
            String code = OrganizationUnitLinkCode.fromOrganization(organization);
            if (code == null || code.isBlank()) {
                continue;
            }
            OrganizationUnit existing = unitByCode.get(code);
            // 已同步过：直接取 OU 主键
            if (existing != null && existing.getId() != null) {
                zxplatOuIds.add(existing.getId());
                continue;
            }
            // 首次或缺链：单条补建（冷路径，稳态登录不应再进）
            ensureOrganizationUnitId(businessOrgId).ifPresent(zxplatOuIds::add);
        }
        return zxplatOuIds;
    }

    /**
     * 查找或创建与 HIS 组织关联的 ZXPlat OU。
     *
     * @param businessOrganizationId HIS 组织主键
     * @return ZXPlat OU 主键；组织不存在或无法生成 code 时为空
     */
    public Optional<Long> ensureOrganizationUnitId(Long businessOrganizationId) {
        if (businessOrganizationId == null) {
            return Optional.empty();
        }
        Optional<Long> existing = organizationUnitScopeBridge
            .findZxplatOrganizationUnitIdByBusinessOrganizationId(businessOrganizationId);
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
        OrganizationUnit ou = organizationUnitAppService.findOptionalByCodeIncludingDeleted(code)
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
     * @param ou           待更新的 ZXPlat OU
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
     * @param ou           待恢复的 ZXPlat OU
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
