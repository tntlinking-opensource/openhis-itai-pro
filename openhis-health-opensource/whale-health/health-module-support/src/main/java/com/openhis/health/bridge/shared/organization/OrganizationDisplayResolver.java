/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.bridge.shared.organization;

import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;

/**
 * Bridge 共享：科室/机构展示名解析（封装 {@link OrganizationQueryService}）。
 */
@Component
@RequiredArgsConstructor
public class OrganizationDisplayResolver {

    private final OrganizationQueryService organizationQueryService;

    public String resolveDisplayName(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(organizationId);
        if (organization != null && StringUtils.hasText(organization.getName())) {
            return organization.getName().trim();
        }
        return String.valueOf(organizationId);
    }

    public String resolveBusNoOrId(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(organizationId);
        if (organization != null && StringUtils.hasText(organization.getBusNo())) {
            return organization.getBusNo().trim();
        }
        return String.valueOf(organizationId);
    }

    public Map<Long, String> resolveDisplayNames(Collection<Long> organizationIds) {
        return organizationQueryService.findOrganizationNamesByIds(organizationIds);
    }
}
