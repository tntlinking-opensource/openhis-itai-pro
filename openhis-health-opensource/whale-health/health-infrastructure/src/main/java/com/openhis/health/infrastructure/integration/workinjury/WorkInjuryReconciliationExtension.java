/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.workinjury;

import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;

import java.util.Map;

/**
 * 工伤对账扩展点。
 */
public interface WorkInjuryReconciliationExtension {

    default boolean isReconciliationEnabled() {
        return false;
    }

    default PrepareWorkInjuryInvokeExtensionOut startRecon(
            Long contractId,
            Long operatorId,
            Map<String, Object> formContext) {
        throw new UnsupportedOperationException("工伤对账未装配");
    }
}
