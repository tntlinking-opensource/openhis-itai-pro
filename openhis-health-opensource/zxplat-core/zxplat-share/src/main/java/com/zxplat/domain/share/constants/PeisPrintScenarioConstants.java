/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * PEIS 打印场景码（与 prt_template.scenario_code、前端 PRINT_SCENARIO 一致）。
 */
public final class PeisPrintScenarioConstants {

    private PeisPrintScenarioConstants() {
    }

    /** 体检总检报告单份打印。 */
    public static final String REPORT_PRINT = "peis-report-print";

    /** 体检毕业合打（预留；当前前端逐份调用 REPORT_PRINT）。 */
    public static final String REPORT_GRAD_COMBINED = "peis-report-grad-print";

    /** 体检指引单（登记台导检单）。 */
    public static final String GUIDE_PRINT = "peis-guide-print";
}
