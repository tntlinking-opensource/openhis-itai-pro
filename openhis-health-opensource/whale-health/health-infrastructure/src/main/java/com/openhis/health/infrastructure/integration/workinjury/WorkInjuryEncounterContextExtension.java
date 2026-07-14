/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.workinjury;

import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryEncounterContextQueryExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryEncounterContextExtensionOut;

/**
 * 就诊维度工伤上下文查询扩展点。
 */
public interface WorkInjuryEncounterContextExtension {

    default WorkInjuryEncounterContextExtensionOut resolveContext(WorkInjuryEncounterContextQueryExtensionIn query) {
        return new WorkInjuryEncounterContextExtensionOut()
                .setWorkInjuryEncounter(false)
                .setQualificationValid(false);
    }
}
