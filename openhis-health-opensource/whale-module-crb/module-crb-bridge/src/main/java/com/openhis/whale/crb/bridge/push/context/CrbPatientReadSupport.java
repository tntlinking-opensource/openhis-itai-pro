/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.context;

import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 患者主数据读支持（health-domain QueryService，非 Extension）。
 */
@Component
@RequiredArgsConstructor
public class CrbPatientReadSupport {

    private final PatientQueryService patientQueryService;

    public Optional<Patient> loadById(Long patientId) {
        if (patientId == null) {
            return Optional.empty();
        }
        return patientQueryService.findOptionalPatientEntityById(patientId);
    }
}
