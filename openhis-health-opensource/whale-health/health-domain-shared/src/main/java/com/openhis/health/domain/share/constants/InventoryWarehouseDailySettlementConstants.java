/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 库房日结报表场景字面量（**AS-INV-RPT-001** · **INV-SET-SDD-R-010**）。
 */
public interface InventoryWarehouseDailySettlementConstants {

    /**
     * 快照汇总候选行上限（对齐 **AS-INV-WHS-004 IF-05** · **DM-DOC-001 §6.2**）。
     */
    Integer SNAPSHOT_SUM_MAX_ROWS = 30000;

    /** 备份单号日期段长度（{@code ABU}+{@code yyyy-MM-dd}+序号）。 */
    Integer BACKUP_BUS_NO_DATE_SEGMENT_LENGTH = 10;

    /** 库房日结 Excel 默认文件名（不含路径）。 */
    String DAILY_SETTLEMENT_EXPORT_DEFAULT_FILENAME = "库房日结.xlsx";

    /** 动态列值类型：数量。 */
    String COLUMN_VALUE_TYPE_QUANTITY = "QUANTITY";

    /** 动态列值类型：进价金额。 */
    String COLUMN_VALUE_TYPE_AMOUNT_COST = "AMOUNT_COST";

    /** 动态列值类型：售价金额。 */
    String COLUMN_VALUE_TYPE_AMOUNT_SALE = "AMOUNT_SALE";

    /** 动态列值类型：包装单位进价。 */
    String COLUMN_VALUE_TYPE_PACKAGE_UNIT_PRICE_COST = "PACKAGE_UNIT_PRICE_COST";

    /** 动态列值类型：包装单位售价。 */
    String COLUMN_VALUE_TYPE_PACKAGE_UNIT_PRICE_SALE = "PACKAGE_UNIT_PRICE_SALE";
}
