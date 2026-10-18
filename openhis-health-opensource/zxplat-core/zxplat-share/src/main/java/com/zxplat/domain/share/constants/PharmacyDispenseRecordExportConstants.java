/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 药房发退药记录导出字面量（AS-OP-PHA-004 / AS-IP-PHA-004 §7 NFR）。
 */
public interface PharmacyDispenseRecordExportConstants {

    /** 导出行数上限（AS §7 建议 5000）。 */
    int EXPORT_MAX_ROWS = 5000;

    /** Office Open XML 电子表格 MIME。 */
    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 门诊发退药记录 Excel 工作表名。 */
    String OUTPATIENT_SHEET_NAME = "门诊发退药记录";

    /** 住院发退药记录 Excel 工作表名。 */
    String INPATIENT_SHEET_NAME = "住院发退药记录";

    /** 门诊发退药记录默认导出文件名。 */
    String OUTPATIENT_EXPORT_DEFAULT_FILENAME = "门诊发退药记录.xlsx";

    /** 住院发退药记录默认导出文件名。 */
    String INPATIENT_EXPORT_DEFAULT_FILENAME = "住院发退药记录.xlsx";
}
