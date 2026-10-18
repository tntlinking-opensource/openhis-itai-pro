/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 追溯码查询报表导出字面量（AS-CUS-TRC-002-SDD · TRC-RPT-SDD-R-005）。
 */
public interface TraceNumberReportExportConstants {

    /** 导出行数上限（AS-CUS-TRC-002 §7）。 */
    int EXPORT_MAX_ROWS = 30000;

    /** 导出循环分页步长。 */
    int EXPORT_PAGE_SIZE = 500;

    /** Excel 工作表名。 */
    String SHEET_NAME = "追溯码查询";

/** TraceNumberReportExport 导出/报表常量 {@code FILE_NAME_PREFIX}，值 {@code 追溯码查询}。 */
    String FILE_NAME_PREFIX = "追溯码查询";
}
