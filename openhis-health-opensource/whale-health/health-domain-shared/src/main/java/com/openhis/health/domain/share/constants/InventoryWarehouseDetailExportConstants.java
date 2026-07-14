/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;


/**

 * 药库库存明细导出工作簿相关字面量（MIME、工作表名、行上限）。

 */

public interface InventoryWarehouseDetailExportConstants {


    /**

     * 库存明细 Excel 工作表名（AS BR-INV-DTL-012）。

     */

/** InventoryWarehouseDetailExportConstants：INVENTORY_DETAIL_SHEET_NAME，值 {@code 库存明细记录}。 */
    String INVENTORY_DETAIL_SHEET_NAME = "库存明细记录";


    /**

     * Office Open XML 电子表格 MIME（AS-SDD 现库导出响应）。

     */

/** InventoryWarehouseDetailExportConstants：XLSX_CONTENT_TYPE。 */
    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";


    /**

     * 现库底部汇总参与求和的行数上限（AS BR-INV-DTL-009）。

     */

/** InventoryWarehouseDetailExportConstants：INVENTORY_DETAIL_SUM_MAX_ROWS，值 {@code 30000}。 */
    Integer INVENTORY_DETAIL_SUM_MAX_ROWS = 30000;


    /**

     * 现库导出默认文件名（不含路径）。

     */

/** InventoryWarehouseDetailExportConstants：INVENTORY_DETAIL_EXPORT_DEFAULT_FILENAME，值 {@code 库存明细.xlsx}。 */
    String INVENTORY_DETAIL_EXPORT_DEFAULT_FILENAME = "库存明细.xlsx";

    /**
     * 备份库存导出参与行数上限（AS-SDD IF-INV-DTL-07 · 100000）。
     */
    Integer INVENTORY_DETAIL_BACKUP_EXPORT_MAX_ROWS = 100000;

    /**
     * 备份库存导出默认文件名（不含路径）。
     */
    String INVENTORY_DETAIL_BACKUP_EXPORT_DEFAULT_FILENAME = "库存明细备份.xlsx";

    /**
     * 定时快照分页拉取每页行数（AS-SDD INV-DTL-SDD-R-008）。
     */
    Integer INVENTORY_DETAIL_SNAPSHOT_PAGE_SIZE = 1000;

}
