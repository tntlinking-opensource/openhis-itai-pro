/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.shared.identity;

import com.openhis.health.module.querysupport.administration.PractitionerManagedScopeLoader;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.practitionerrole.queries.model.PractitionerManagedScopeQueryModel;
import com.openhis.whale.core.application.IPractitionerManagedScopeResolver;
import com.openhis.whale.core.application.dto.PractitionerManagedScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * whale-core {@link IPractitionerManagedScopeResolver} 的 health 实现：从 {@code adm_pr_assign} 派生管辖范围
 * （药库/药房 {@code adm_location.id}、科室 {@code adm_organization.id}、病区 {@code adm_location.id}）。
 */
@Component
@RequiredArgsConstructor
public class HealthPractitionerManagedScopeResolver implements IPractitionerManagedScopeResolver {

    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerManagedScopeLoader practitionerManagedScopeLoader;

    @Override
    public Optional<PractitionerManagedScope> resolve(Long userId, Long practitionerId, Long tenantId) {
        Long resolvedPractitionerId = practitionerId;
        if (resolvedPractitionerId == null && userId != null) {
            var practitioner = practitionerQueryService.findByUserId(userId);
            if (practitioner != null) {
                resolvedPractitionerId = practitioner.getId();
            }
        }
        if (resolvedPractitionerId == null) {
            return Optional.empty();
        }
        PractitionerManagedScopeQueryModel model =
            this.practitionerManagedScopeLoader.resolveManagedScope(resolvedPractitionerId);
        PractitionerManagedScope scope = new PractitionerManagedScope(
            model.getManagedLocationIds(),
            model.getManagedOrganizationUnitIds(),
            model.getManagedWardLocationIds());
        return Optional.of(scope);
    }
}
