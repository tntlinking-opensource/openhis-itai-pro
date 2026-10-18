/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 护士工作量统计导出字面量（AS-RPT-002 · NWL-SDD-R-005/006）。
 */
public interface NurseWorkloadExportConstants {

    /** 导出行数上限（AS-RPT-002 §7）。 */
    int EXPORT_MAX_ROWS = 100000;

    /** 导出循环分页步长（禁止单次超大 pageSize）。 */
    int EXPORT_PAGE_SIZE = 500;

    /** 诊疗执行明细 Excel 工作表名。 */
    String EXECUTION_SHEET_NAME = "诊疗执行明细";

    /** 耗材使用明细 Excel 工作表名。 */
    String CONSUMABLE_SHEET_NAME = "耗材使用明细";

    /** 导出主 Tab 展示名：诊疗执行。 */
    String MAIN_TAB_EXECUTION_LABEL = "诊疗执行";

    /** 导出主 Tab 展示名：耗材使用。 */
    String MAIN_TAB_CONSUMABLE_LABEL = "耗材使用";
}
