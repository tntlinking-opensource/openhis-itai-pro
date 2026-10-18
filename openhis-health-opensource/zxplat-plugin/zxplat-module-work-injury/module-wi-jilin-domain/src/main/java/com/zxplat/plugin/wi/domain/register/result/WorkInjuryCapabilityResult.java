/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.register.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 工伤模块 capability（前端门控）。 */
@Data
@Accessors(chain = true)
public class WorkInjuryCapabilityResult {

    private boolean enabled;
    private boolean winRequired;
    private String fixmedinsCode;
    private String contractBusNo;
}
