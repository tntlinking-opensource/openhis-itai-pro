/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement;

/** 2206/2303 预结算快照状态 */
public enum YbPreSettleStatus {

    ACTIVE,
    SETTLED,
    CANCELLED,
    REVOKED;

    public boolean matches(String status) {
        return name().equals(status);
    }
}
