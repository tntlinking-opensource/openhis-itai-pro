/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 单据详情报表导出与汇总相关常量（AS-INV-RPT-002 · INV-DDC-SDD-R-005/006/009）。
 */
public interface DocumentDetailExportConstants {

    /** Excel 工作表名（明细导出）。 */
    String SHEET_NAME = "单据详情";

    /** Excel 工作表名（按品规汇总导出）。 */
    String SUMMARY_SHEET_NAME = "单据详情汇总";

    /** 全量拉取/合计/导出分页步长。 */
    int EXPORT_PAGE_SIZE = 500;

    /** 导出最大明细行数；超出失败 {@code INV_DDC_205}。 */
    int EXPORT_MAX_ROWS = 100000;

    /**
     * 导出/展示模式：明细行平铺（默认；与历史客户端兼容）。
     */
    String PRESENTATION_MODE_DETAIL = "DETAIL";

    /**
     * 导出/展示模式：按品规汇总（同筛选条件对 supply 明细行聚合）。
     */
    String PRESENTATION_MODE_SUMMARY = "SUMMARY";

    /**
     * 汇总维度：按品规（{@code item_table + item_id}）；本期仅此维。
     */
    String SUMMARY_DIMENSION_ITEM = "ITEM";

    /** 汇总维度下拉展示：按品规。 */
    String SUMMARY_DIMENSION_ITEM_LABEL = "按品规";

    /** 药库域导出文件名前缀。 */
    String WAREHOUSE_FILE_NAME_PREFIX = "药库单据详情";

    /** 药房域导出文件名前缀。 */
    String PHARMACY_FILE_NAME_PREFIX = "药房单据详情";

    /** 科室药房域导出文件名前缀。 */
    String DEPARTMENT_FILE_NAME_PREFIX = "科室单据详情";
}
