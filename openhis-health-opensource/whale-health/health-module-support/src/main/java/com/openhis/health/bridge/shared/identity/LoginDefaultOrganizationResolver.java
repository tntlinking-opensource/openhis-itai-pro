/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.bridge.shared.identity;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.whale.core.application.ILoginDefaultOrganizationResolver;
import com.openhis.whale.core.application.ILoginOrganizationScopeQuery;
import com.openhis.whale.core.application.dto.LoginOrganizationOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 默认登录组织：{@code adm_practitioner.org_id}；未填时回落会话医院。
 */
@Component
@RequiredArgsConstructor
public class LoginDefaultOrganizationResolver implements ILoginDefaultOrganizationResolver {

    private final PractitionerQueryService practitionerQueryService;
    private final ILoginOrganizationScopeQuery loginOrganizationScopeQuery;

    @Override
    public Optional<Long> resolveDefaultLoginOrganizationId(Long userId, Long sessionHospitalOrganizationId) {
        if (userId == null) {
            return Optional.empty();
        }
        Practitioner practitioner = practitionerQueryService.findByUserId(userId);
        if (practitioner != null && practitioner.getOrgId() != null) {
            return Optional.of(practitioner.getOrgId());
        }
        if (sessionHospitalOrganizationId != null) {
            return Optional.of(sessionHospitalOrganizationId);
        }
        return Optional.empty();
    }

    @Override
    public List<Long> resolveFallbackHospitalOrganizationIds(Long userId) {
        if (userId == null || loginOrganizationScopeQuery == null) {
            return List.of();
        }
        if (practitionerQueryService.findByUserId(userId) == null) {
            return List.of();
        }
        List<Long> hospitalIds = new ArrayList<>();
        for (LoginOrganizationOption option : loginOrganizationScopeQuery.listActiveHospitalOrganizations()) {
            if (option != null && option.getId() != null) {
                hospitalIds.add(option.getId());
            }
        }
        return hospitalIds;
    }
}
