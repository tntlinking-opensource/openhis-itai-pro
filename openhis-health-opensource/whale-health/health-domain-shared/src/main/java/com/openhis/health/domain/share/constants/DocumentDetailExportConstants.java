/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 单据详情报表导出相关常量（AS-INV-RPT-002 · INV-DDC-SDD-R-005/006）。
 */
public interface DocumentDetailExportConstants {

    /** Excel 工作表名。 */
    String SHEET_NAME = "单据详情";

    /** 全量拉取/合计/导出分页步长。 */
    int EXPORT_PAGE_SIZE = 500;

    /** 导出最大明细行数；超出失败 {@code INV_DDC_205}。 */
    int EXPORT_MAX_ROWS = 100000;

    /** 药库域导出文件名前缀。 */
    String WAREHOUSE_FILE_NAME_PREFIX = "药库单据详情";

    /** 药房域导出文件名前缀。 */
    String PHARMACY_FILE_NAME_PREFIX = "药房单据详情";

    /** 科室药房域导出文件名前缀。 */
    String DEPARTMENT_FILE_NAME_PREFIX = "科室单据详情";
}
