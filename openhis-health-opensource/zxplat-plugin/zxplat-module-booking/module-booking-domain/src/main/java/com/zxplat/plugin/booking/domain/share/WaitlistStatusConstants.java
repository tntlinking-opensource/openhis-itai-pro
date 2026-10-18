/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.share;

/**
 * 候补队列状态（BR-EXT-011）。
 */
public final class WaitlistStatusConstants {

    public static final String WAITING = "WAITING";
    public static final String PROMOTED = "PROMOTED";
    public static final String CANCELLED = "CANCELLED";

    private WaitlistStatusConstants() {
    }

    public static boolean isValid(String status) {
        return WAITING.equals(status) || PROMOTED.equals(status) || CANCELLED.equals(status);
    }
}
