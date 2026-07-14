/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.support;

/**
 * CRB 国标 id 格式化。
 */
public final class CrbIdFormatter {

    private CrbIdFormatter() {
    }

    public static String formatPatientId(Long patientId) {
        return patientId == null ? "" : "P" + patientId;
    }

    public static String formatEncounterId(Long encounterId) {
        return encounterId == null ? "" : "E" + encounterId;
    }

    public static String formatUserId(Long userId) {
        return userId == null ? "" : "U" + userId;
    }

    public static String formatDeptId(Long locationId) {
        return locationId == null ? "" : "D" + locationId;
    }
}
