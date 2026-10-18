/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院发药报表（AS-RPT-004）异常码与导出字面量。
 */
public interface InpatientMedicationDispenseReportConstants {

    /** 页面初始化失败。 */
    String EX_IMD_101 = "EX-RPT-IMD-101";

    /** 日期非法。 */
    String EX_IMD_102 = "EX-RPT-IMD-102";

    /** 查询失败。 */
    String EX_IMD_103 = "EX-RPT-IMD-103";

    /** 导出失败。 */
    String EX_IMD_104 = "EX-RPT-IMD-104";

    /** Excel 工作表名。 */
    String EXPORT_SHEET_NAME = "住院药品使用明细";

    /** 默认导出文件名。 */
    String EXPORT_DEFAULT_FILENAME = "住院药品使用明细.xlsx";
}
