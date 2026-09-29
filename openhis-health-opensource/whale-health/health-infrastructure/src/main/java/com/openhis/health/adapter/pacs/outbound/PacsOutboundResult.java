/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.outbound;

import lombok.Getter;

/**
 * 外发结果。
 */
@Getter
public class PacsOutboundResult {

    private final boolean success;
    private final String message;
    private final String payload;

    public PacsOutboundResult(boolean success, String message, String payload) {
        this.success = success;
        this.message = message;
        this.payload = payload;
    }

    public static PacsOutboundResult ok(String payload) {
        return new PacsOutboundResult(true, "OK", payload);
    }

    public static PacsOutboundResult fail(String message) {
        return new PacsOutboundResult(false, message, null);
    }
}
