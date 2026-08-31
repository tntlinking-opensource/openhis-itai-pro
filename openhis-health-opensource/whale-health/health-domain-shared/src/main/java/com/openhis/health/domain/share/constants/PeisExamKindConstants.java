/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 体检类型（写入 {@code peis_encounter_ext.package_snapshot_json.examKind}）。
 */
public final class PeisExamKindConstants {

    /** 个人体检（院内个检）。 */
    public static final String PERSONAL = "personal";

    /** 团体体检（院内团检，须指定单位与分组）。 */
    public static final String GROUP = "group";

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
        return PERSONAL.equals(k)
            || GROUP.equals(k)
            || FRESHMAN.equals(k)
            || ANNUAL.equals(k)
            || STAFF.equals(k)
            || OTHER.equals(k);
    }

    /**
     * 院内个检/团检类型，不套用校医院「本年度已有有效体检」规则。
     */
    public static boolean isHospitalPeKind(String examKind) {
        return PERSONAL.equals(examKind) || GROUP.equals(examKind);
    }

    public static String normalizeOrNull(String examKind) {
        if (examKind == null || examKind.isBlank()) {
            return null;
        }
        String k = examKind.trim();
        return isKnown(k) ? k : OTHER;
    }
}
