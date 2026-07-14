/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.platform.organization;

import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.whale.core.application.IOrganizationUnitScopeBridge;
import com.openhis.whale.identity.application.OrganizationUnitAppService;
import com.openhis.whale.identity.domain.OrganizationUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * HIS 组织 id 与 Whale {@link OrganizationUnit} id 的 code 桥接实现。
 */
@Component
@RequiredArgsConstructor
public class HealthOrganizationUnitScopeAdapter implements IOrganizationUnitScopeBridge {

    private final OrganizationQueryService organizationQueryService;
    private final OrganizationUnitAppService organizationUnitAppService;

    /**
     * HIS 业务组织 id → Whale {@link OrganizationUnit} id（经 {@link OrganizationUnitLinkCode} 桥接）。
     *
     * @param businessOrganizationId {@code adm_organization.id}
     * @return Whale OU 主键；无法解析 code 或未同步时 empty
     */
    @Override
    public Optional<Long> findWhaleOrganizationUnitIdByBusinessOrganizationId(Long businessOrganizationId) {
        if (businessOrganizationId == null) {
            return Optional.empty();
        }
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(businessOrganizationId);
        if (organization == null) {
            return Optional.empty();
        }
        String code = OrganizationUnitLinkCode.fromOrganization(organization);
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return organizationUnitAppService.findOptionalByCode(code).map(OrganizationUnit::getId);
    }

    /**
     * Whale {@link OrganizationUnit} id → HIS 业务组织 id（优先解析 ORG_ 前缀 code，否则按 busNo 匹配）。
     *
     * @param whaleOrganizationUnitId Whale OU 主键
     * @return {@code adm_organization.id}；未命中时 empty
     */
    @Override
    public Optional<Long> findBusinessOrganizationIdByWhaleOrganizationUnitId(Long whaleOrganizationUnitId) {
        if (whaleOrganizationUnitId == null) {
            return Optional.empty();
        }
        return organizationUnitAppService.findOptionalById(whaleOrganizationUnitId)
            .flatMap(ou -> resolveBusinessOrganizationIdFromCode(ou.getCode()));
    }

    /**
     * 组织单元 code → 业务 organizationId：优先 ORG 前缀解析，否则 busNo 查组织。
     *
     * @param code 组织单元编码
     * @return 业务组织 id；无法解析 empty
     */
    private Optional<Long> resolveBusinessOrganizationIdFromCode(String code) {
        Optional<Long> fromOrgPrefix = OrganizationUnitLinkCode.parseBusinessOrganizationId(code);
        if (fromOrgPrefix.isPresent()) {
            return fromOrgPrefix;
        }
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Organization organization = organizationQueryService.findFirstByBusNo(code.trim());
        if (organization == null || organization.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(organization.getId());
    }
}
