/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

/** 进销存平台字段编码辅助。 */
public final class InventoryPlatformFieldSupport {

    private InventoryPlatformFieldSupport() {
    }

    public static String toRxFlagCode(Boolean rxFlag) {
        if (Boolean.TRUE.equals(rxFlag)) {
            return "1";
        }
        return "0";
    }
}
