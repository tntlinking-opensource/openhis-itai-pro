/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * PACS 打印场景码（与 {@code prt_template.scenario_code}、前端 PRINT_SCENARIO 一致）。
 */
public final class PacsPrintScenarioConstants {

    private PacsPrintScenarioConstants() {
    }

    /** 检查登记条（登记工作台 REG-05）。 */
    public static final String REGISTRATION_SLIP_PRINT = "pacs-registration-slip-print";
}
