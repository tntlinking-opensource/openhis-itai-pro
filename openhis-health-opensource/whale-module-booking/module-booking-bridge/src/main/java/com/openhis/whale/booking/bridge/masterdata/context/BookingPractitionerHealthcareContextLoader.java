/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.bridge.masterdata.context;

import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 医师可接诊号源类型收窄（bridge 内聚，替代 health-module-support）。 */
@Component
@RequiredArgsConstructor
public class BookingPractitionerHealthcareContextLoader {

    private final PractitionerRoleQueryService practitionerRoleQueryService;

    public Set<Long> loadHealthcareServiceIdsByPractitioner(Long practitionerId) {
        if (practitionerId == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(
                practitionerRoleQueryService.listHealthcareServiceIdsByPractitioner(practitionerId));
    }

    public List<HealthcareService> narrowByPractitioner(List<HealthcareService> allServices, Long practitionerId) {
        if (practitionerId == null || allServices == null) {
            return allServices;
        }
        Set<Long> allowedIds = loadHealthcareServiceIdsByPractitioner(practitionerId);
        if (allowedIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<HealthcareService> narrowed = new ArrayList<>();
        for (HealthcareService service : allServices) {
            if (service != null && service.getId() != null && allowedIds.contains(service.getId())) {
                narrowed.add(service);
            }
        }
        return narrowed;
    }

    public void assertPractitionerAcceptsHealthcareService(Long practitionerId, Long healthcareServiceId) {
        if (practitionerId == null) {
            return;
        }
        if (healthcareServiceId == null) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "号源类型不能为空");
        }
        Set<Long> allowedIds = loadHealthcareServiceIdsByPractitioner(practitionerId);
        if (allowedIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT,
                    "该医师未配置可接诊号源，请在用户管理中维护");
        }
        if (!allowedIds.contains(healthcareServiceId)) {
            throw new BusinessException(
                    ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT,
                    "所选号源类型不在该医师可接诊范围内");
        }
    }
}
