/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 门诊发药报表（AS-RPT-003）异常码与导出字面量。
 */
public interface OutpatientMedicationDispenseReportConstants {

    /** 页面初始化失败。 */
    String EX_OMD_101 = "EX-RPT-OMD-101";

    /** 日期非法。 */
    String EX_OMD_102 = "EX-RPT-OMD-102";

    /** 查询失败。 */
    String EX_OMD_103 = "EX-RPT-OMD-103";

    /** 导出失败。 */
    String EX_OMD_104 = "EX-RPT-OMD-104";

    /** Excel 工作表名。 */
    String EXPORT_SHEET_NAME = "门诊药品使用明细";

    /** 默认导出文件名。 */
    String EXPORT_DEFAULT_FILENAME = "门诊药品使用明细.xlsx";
}
