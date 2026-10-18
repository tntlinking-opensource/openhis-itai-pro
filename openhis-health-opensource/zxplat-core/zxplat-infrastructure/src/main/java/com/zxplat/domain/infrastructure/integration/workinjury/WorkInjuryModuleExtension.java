/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury;

import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryCapabilityExtensionOut;

/**
 * 工伤 Module capability 门控（前端入口、费用性质 scheme）。
 */
public interface WorkInjuryModuleExtension {

    default boolean isEnabled() {
        return false;
    }

    default boolean supportsContract(Long contractId) {
        return false;
    }

    default WorkInjuryCapabilityExtensionOut capability() {
        return new WorkInjuryCapabilityExtensionOut().setEnabled(false);
    }
}
