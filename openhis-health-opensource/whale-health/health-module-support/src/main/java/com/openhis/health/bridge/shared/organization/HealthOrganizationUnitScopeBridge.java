/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.bridge.shared.organization;

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
public class HealthOrganizationUnitScopeBridge implements IOrganizationUnitScopeBridge {

    private final OrganizationQueryService organizationQueryService;
    private final OrganizationUnitAppService organizationUnitAppService;

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

    @Override
    public Optional<Long> findBusinessOrganizationIdByWhaleOrganizationUnitId(Long whaleOrganizationUnitId) {
        if (whaleOrganizationUnitId == null) {
            return Optional.empty();
        }
        return organizationUnitAppService.findOptionalById(whaleOrganizationUnitId)
            .flatMap(ou -> resolveBusinessOrganizationIdFromCode(ou.getCode()));
    }

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
