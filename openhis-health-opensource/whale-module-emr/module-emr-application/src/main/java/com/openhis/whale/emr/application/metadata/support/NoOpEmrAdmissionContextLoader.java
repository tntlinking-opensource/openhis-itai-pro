/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.metadata.support;

import com.openhis.whale.emr.domain.loader.EmrAdmissionContextLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 无 HIS 对接时的空实现；health 模块可提供覆盖 Bean。
 */
@Component
@ConditionalOnMissingBean(EmrAdmissionContextLoader.class)
public class NoOpEmrAdmissionContextLoader implements EmrAdmissionContextLoader {

    @Override
    public void contributePatient(Map<String, String> target, String patientId) {
        // no-op
    }

    @Override
    public void contributeEncounter(Map<String, String> target, String encounterId) {
        // no-op
    }

    @Override
    public void contributePractitioner(Map<String, String> target, Long practitionerId) {
        // no-op
    }
}
