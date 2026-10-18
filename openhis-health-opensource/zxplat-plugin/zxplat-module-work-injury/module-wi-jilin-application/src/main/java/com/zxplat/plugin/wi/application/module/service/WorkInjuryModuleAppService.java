/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.application.module.service;

import com.zxplat.plugin.wi.domain.register.access.WorkInjuryCapabilityAccess;
import com.zxplat.plugin.wi.domain.register.result.WorkInjuryCapabilityResult;
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
