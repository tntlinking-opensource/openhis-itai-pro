/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 校医院体检类型（写入 {@code peis_encounter_ext.package_snapshot_json.examKind}）。
 */
public final class PeisExamKindConstants {

    public static final String FRESHMAN = "freshman";
    public static final String ANNUAL = "annual";
    public static final String STAFF = "staff";
    public static final String OTHER = "other";

    private PeisExamKindConstants() {
    }

    public static boolean isKnown(String examKind) {
        if (examKind == null || examKind.isBlank()) {
            return false;
        }
        String k = examKind.trim();
        return FRESHMAN.equals(k) || ANNUAL.equals(k) || STAFF.equals(k) || OTHER.equals(k);
    }

    public static String normalizeOrNull(String examKind) {
        if (examKind == null || examKind.isBlank()) {
            return null;
        }
        String k = examKind.trim();
        return isKnown(k) ? k : OTHER;
    }
}
