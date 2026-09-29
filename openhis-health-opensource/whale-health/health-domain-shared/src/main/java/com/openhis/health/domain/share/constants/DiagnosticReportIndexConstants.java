/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 报告读侧索引常量（cln_diagnostic_report_catalog，DM-CLN-006）。
 */
public final class DiagnosticReportIndexConstants {

    private DiagnosticReportIndexConstants() {
    }

    /** 来源模块：体检总检。 */
    public static final String MODULE_PEIS = "PEIS";

    /** 来源模块：检查报告。 */
    public static final String MODULE_PACS = "PACS";

    /** 来源模块：检验报告。 */
    public static final String MODULE_LIS = "LIS";

    /** 索引状态：已定稿可打印。 */
    public static final String STATUS_FINAL = "final";

    /** 索引状态：取消审核后吊销（医生站不可见；再审核可 upsert 回 final）。 */
    public static final String STATUS_CANCELLED = "cancelled";

    /** 唯一键前缀：{@code PEIS:{sourceReportId}}。 */
    public static final String UNIQUE_KEY_PREFIX_PEIS = "PEIS:";

    /** 唯一键前缀：{@code PACS:{sourceReportId}}。 */
    public static final String UNIQUE_KEY_PREFIX_PACS = "PACS:";
}
