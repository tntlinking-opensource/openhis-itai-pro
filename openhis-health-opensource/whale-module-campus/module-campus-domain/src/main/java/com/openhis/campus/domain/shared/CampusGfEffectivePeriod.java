/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.shared;

import java.time.LocalDateTime;

/**
 * 公费比例有效期判定。
 */
public final class CampusGfEffectivePeriod {

    private CampusGfEffectivePeriod() {
    }

    public static boolean isEffective(LocalDateTime at, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        LocalDateTime point = at != null ? at : LocalDateTime.now();
        if (effectiveFrom != null && point.isBefore(effectiveFrom)) {
            return false;
        }
        if (effectiveTo != null && point.isAfter(effectiveTo)) {
            return false;
        }
        return true;
    }
}
