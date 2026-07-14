/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.workinjury.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 前端 capability 门控（是否启用工伤、Win 必填等）。 */
@Data
@Accessors(chain = true)
public class WorkInjuryCapabilityExtensionOut {

    private boolean enabled;
    private boolean winRequired;
    private String fixmedinsCode;
    private String contractBusNo;
}
