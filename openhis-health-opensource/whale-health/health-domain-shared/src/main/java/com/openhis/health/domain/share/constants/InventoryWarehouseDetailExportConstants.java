/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.constants;


/**

 * 药库库存明细导出工作簿相关字面量（MIME、工作表名、行上限）。

 */

public interface InventoryWarehouseDetailExportConstants {


    /**

     * 库存明细 Excel 工作表名（AS BR-INV-DTL-012）。

     */

    String INVENTORY_DETAIL_SHEET_NAME = "库存明细记录";


    /**

     * Office Open XML 电子表格 MIME（AS-SDD 现库导出响应）。

     */

    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";


    /**

     * 现库底部汇总参与求和的行数上限（AS BR-INV-DTL-009）。

     */

    Integer INVENTORY_DETAIL_SUM_MAX_ROWS = 30000;


    /**

     * 现库导出默认文件名（不含路径）。

     */

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
