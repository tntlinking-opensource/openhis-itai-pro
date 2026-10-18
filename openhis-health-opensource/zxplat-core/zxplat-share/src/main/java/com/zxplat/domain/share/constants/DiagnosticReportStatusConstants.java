/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 临床总检 DiagnosticReport 状态 code（cln_diagnostic_report.status_enum，DM-CLN-008）。
 */
public final class DiagnosticReportStatusConstants {

    private DiagnosticReportStatusConstants() {
    }

    /** 草稿/初步（MAS-03）。 */
    public static final String STATUS_PRELIMINARY = "preliminary";

    /** 已定稿（{@code final} 为 Java 关键字，常量名加前缀）。 */
    public static final String STATUS_FINAL = "final";

    /** 已修订（P1）。 */
    public static final String STATUS_AMENDED = "amended";

    /** 已作废（P1）。 */
    public static final String STATUS_CANCELLED = "cancelled";
}
