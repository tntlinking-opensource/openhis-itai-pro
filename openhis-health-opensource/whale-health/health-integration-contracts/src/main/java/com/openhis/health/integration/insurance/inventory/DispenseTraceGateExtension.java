/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory;

import com.openhis.health.integration.insurance.inventory.command.DispenseTraceGateSnapshot;

import java.util.List;

/** 发药前追溯码门控扩展点。 */
public interface DispenseTraceGateExtension {

    default boolean isEnabled() {
        return false;
    }

    default void assertTraceAllowedForPrepare(DispenseTraceGateSnapshot snapshot, List<String> submittedTraceCodes) {
    }

    default void assertTraceAllowedForDispense(DispenseTraceGateSnapshot snapshot) {
    }
}
