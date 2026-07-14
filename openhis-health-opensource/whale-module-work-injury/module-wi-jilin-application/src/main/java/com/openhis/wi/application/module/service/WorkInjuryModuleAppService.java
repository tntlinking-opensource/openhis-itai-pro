/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.module.service;

import com.openhis.wi.domain.register.access.WorkInjuryCapabilityAccess;
import com.openhis.wi.domain.register.result.WorkInjuryCapabilityResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 工伤模块 capability（POST /api/app/workInjuryModule/*）。 */
@Service
@RequiredArgsConstructor
public class WorkInjuryModuleAppService {

    private final WorkInjuryCapabilityAccess workInjuryCapabilityAccess;

    public WorkInjuryCapabilityResult getCapability() {
        return workInjuryCapabilityAccess.capability();
    }
}
