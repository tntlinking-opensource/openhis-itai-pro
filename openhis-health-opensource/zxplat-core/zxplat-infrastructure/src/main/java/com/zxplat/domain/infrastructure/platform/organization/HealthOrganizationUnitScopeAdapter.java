/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.platform.organization;

import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.framework.core.application.IOrganizationUnitScopeBridge;
import com.zxplat.framework.identity.application.OrganizationUnitAppService;
import com.zxplat.framework.identity.domain.OrganizationUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HIS 组织 id 与 ZXPlat {@link OrganizationUnit} id 的 code 桥接实现。
 */
@Component
@RequiredArgsConstructor
public class HealthOrganizationUnitScopeAdapter implements IOrganizationUnitScopeBridge {

    private final OrganizationQueryService organizationQueryService;
    private final OrganizationUnitAppService organizationUnitAppService;

    /**
     * HIS 业务组织 id → ZXPlat {@link OrganizationUnit} id（经 {@link OrganizationUnitLinkCode} 桥接）。
     *
     * @param businessOrganizationId {@code adm_organization.id}
     * @return ZXPlat OU 主键；无法解析 code 或未同步时 empty
     */
    @Override
    public Optional<Long> findZxplatOrganizationUnitIdByBusinessOrganizationId(Long businessOrganizationId) {
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
     * ZXPlat {@link OrganizationUnit} id → HIS 业务组织 id（优先解析 ORG_ 前缀 code，否则按 busNo 匹配）。
     *
     * @param zxplatOrganizationUnitId ZXPlat OU 主键
     * @return {@code adm_organization.id}；未命中时 empty
     */
    @Override
    public Optional<Long> findBusinessOrganizationIdByZxplatOrganizationUnitId(Long zxplatOrganizationUnitId) {
        if (zxplatOrganizationUnitId == null) {
            return Optional.empty();
        }
        return organizationUnitAppService.findOptionalById(zxplatOrganizationUnitId)
            .flatMap(ou -> resolveBusinessOrganizationIdFromCode(ou.getCode()));
    }

    /**
     * 由 OU code 反查 HIS 组织主键（登录列表已批量加载 OU 时避免再按 id 读库）。
     *
     * @param organizationUnitCode ZXPlat OU code
     * @return {@code adm_organization.id}；无法解析 empty
     */
    @Override
    public Optional<Long> findBusinessOrganizationIdByOrganizationUnitCode(String organizationUnitCode) {
        return resolveBusinessOrganizationIdFromCode(organizationUnitCode);
    }

    /**
     * 批量 OU code → HIS 组织 id：{@code org-} 前缀内存解析，其余按 busNo 一次查出。
     *
     * @param organizationUnitCodes OU code 集合
     * @return code → 业务组织主键
     */
    @Override
    public Map<String, Long> mapBusinessOrganizationIdsByOrganizationUnitCodes(
            Collection<String> organizationUnitCodes) {
        Map<String, Long> result = new HashMap<>();
        if (organizationUnitCodes == null || organizationUnitCodes.isEmpty()) {
            return result;
        }
        List<String> busNos = new ArrayList<>();
        for (String rawCode : organizationUnitCodes) {
            if (rawCode == null || rawCode.isBlank()) {
                continue;
            }
            String code = rawCode.trim();
            Optional<Long> fromOrgPrefix = OrganizationUnitLinkCode.parseBusinessOrganizationId(code);
            // org-{id}：无需查库
            if (fromOrgPrefix.isPresent()) {
                result.put(code, fromOrgPrefix.get());
                continue;
            }
            busNos.add(code);
        }
        if (busNos.isEmpty()) {
            return result;
        }
        Map<String, Long> idByBusNo = organizationQueryService.mapIdsByBusNos(busNos);
        for (String busNo : busNos) {
            Long organizationId = idByBusNo.get(busNo);
            if (organizationId != null) {
                result.put(busNo, organizationId);
            }
        }
        return result;
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
