/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * MRMS 质控规则 {@code rule_code} 与表达式 JSON 键（与 Flyway 种子、执行器分支对齐）。
 */
public final class MrmsQcRuleCodes {

    /** 首页提交必填项。 */
    public static final String HP_SUBMIT_REQUIRED = "QC_HP_SUBMIT_REQUIRED";

    /** 入出院日期逻辑。 */
    public static final String HP_DATE_LOGIC = "QC_HP_DATE_LOGIC";

    /** 出院主诊断。 */
    public static final String HP_DISCHARGE_MAIN_DIAG = "QC_HP_DISCHARGE_MAIN_DIAG";

    /** 出院后未及时提交首页。 */
    public static final String SUBMIT_OVERDUE = "QC_SUBMIT_OVERDUE";

    /** 出院后未及时归档。 */
    public static final String ARCHIVE_OVERDUE = "QC_ARCHIVE_OVERDUE";

    /** 必写文书齐全。 */
    public static final String DOC_REQUIRED = "QC_DOC_REQUIRED";

    /** {@code expression_json} 中「出院后天数」参数键。 */
    public static final String EXPRESSION_MAX_DAYS_AFTER_DISCHARGE = "maxDaysAfterDischarge";

    /** 提交逾期默认天数（种子未配置时的回退值）。 */
    public static final int DEFAULT_SUBMIT_OVERDUE_DAYS = 30;

    /** 归档逾期默认天数（种子未配置时的回退值）。 */
    public static final int DEFAULT_ARCHIVE_OVERDUE_DAYS = 60;

    private MrmsQcRuleCodes() {
    }
}
