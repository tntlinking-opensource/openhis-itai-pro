/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 库房期初期末明细导出工作簿字面量（**AS-INV-RPT-001** · **INV-SET-SDD-R-009**）。
 */
public interface InventoryPeriodBalanceDetailExportConstants {

    /** 库房期初期末明细 Excel 工作表名（**BR-INV-SET-021**）。 */
    String PERIOD_BALANCE_DETAIL_SHEET_NAME = "库房期初期末明细";

    /**
     * 导出行数上限（与库存明细备份导出同档，默认 100000）。
     */
    Integer EXPORT_MAX_ROWS = 100000;

    /** 导出分页拉取每页行数（禁止单次超大 pageSize 截断）。 */
    Integer EXPORT_PAGE_SIZE = 500;

    /** 院内编码 — DTO 字段名。 */
    String FIELD_ITEM_NO = "itemNo";

    /** 院内编码 — Excel 表头。 */
    String LABEL_ITEM_NO = "院内编码";

    /** 药品名称 — DTO 字段名。 */
    String FIELD_PRODUCT_NAME = "productName";

    /** 药品名称 — Excel 表头。 */
    String LABEL_PRODUCT_NAME = "药品名称";

    /** 商品名 — DTO 字段名。 */
    String FIELD_ITEM_NAME = "itemName";

    /** 商品名 — Excel 表头。 */
    String LABEL_ITEM_NAME = "商品名";

    /** 规格 — DTO 字段名。 */
    String FIELD_SPEC_TEXT = "specText";

    /** 规格 — Excel 表头。 */
    String LABEL_SPEC_TEXT = "规格";

    /** 厂商 — DTO 字段名。 */
    String FIELD_MANUFACTURER_TEXT = "manufacturerText";

    /** 厂商 — Excel 表头。 */
    String LABEL_MANUFACTURER_TEXT = "厂商";

    /** 期初包装进价 — DTO 字段名。 */
    String FIELD_BEGIN_COST_PACKAGE_UNIT_PRICE = "beginCostPackageUnitPrice";

    /** 期初包装进价 — Excel 表头。 */
    String LABEL_BEGIN_COST_PACKAGE_UNIT_PRICE = "期初进价单价(包装)";

    /** 期初数量(最小) — DTO 字段名。 */
    String FIELD_BEGIN_QUANTITY = "beginQuantity";

    /** 期初数量(最小) — Excel 表头。 */
    String LABEL_BEGIN_QUANTITY = "期初数量(最小)";

    /** 期初进价额 — DTO 字段名。 */
    String FIELD_BEGIN_COST_AMOUNT = "beginCostAmount";

    /** 期初进价额 — Excel 表头。 */
    String LABEL_BEGIN_COST_AMOUNT = "期初进价总额";

    /** 期末包装进价 — DTO 字段名。 */
    String FIELD_END_COST_PACKAGE_UNIT_PRICE = "endCostPackageUnitPrice";

    /** 期末包装进价 — Excel 表头。 */
    String LABEL_END_COST_PACKAGE_UNIT_PRICE = "期末进价单价(包装)";

    /** 期末数量(最小) — DTO 字段名。 */
    String FIELD_END_QUANTITY = "endQuantity";

    /** 期末数量(最小) — Excel 表头。 */
    String LABEL_END_QUANTITY = "期末数量(最小)";

    /** 期末进价额 — DTO 字段名。 */
    String FIELD_END_COST_AMOUNT = "endCostAmount";

    /** 期末进价额 — Excel 表头。 */
    String LABEL_END_COST_AMOUNT = "期末进价总额";

    /** 调价进价盈亏 — DTO 字段名。 */
    String FIELD_PRICE_ADJUSTMENT_COST_AMOUNT = "priceAdjustmentCostAmount";

    /** 调价进价盈亏 — Excel 表头。 */
    String LABEL_PRICE_ADJUSTMENT_COST_AMOUNT = "调价";

    /** 推算期末数量 — DTO 字段名。 */
    String FIELD_COMPUTED_END_QUANTITY = "computedEndQuantity";

    /** 推算期末数量 — Excel 表头。 */
    String LABEL_COMPUTED_END_QUANTITY = "推算期末数量(最小)";

    /** 推算期末进价额 — DTO 字段名。 */
    String FIELD_COMPUTED_END_COST_AMOUNT = "computedEndCostAmount";

    /** 推算期末进价额 — Excel 表头。 */
    String LABEL_COMPUTED_END_COST_AMOUNT = "推算期末进价总额";

    /** 盈亏数 — DTO 字段名。 */
    String FIELD_QUANTITY_DIFF = "quantityDiff";

    /** 盈亏数 — Excel 表头。 */
    String LABEL_QUANTITY_DIFF = "盈亏数(最小)";

    /** 盈亏差 — DTO 字段名。 */
    String FIELD_COST_AMOUNT_DIFF = "costAmountDiff";

    /** 盈亏差 — Excel 表头。 */
    String LABEL_COST_AMOUNT_DIFF = "盈亏差";

    /** 是否账平 — DTO 字段名。 */
    String FIELD_IS_BALANCE = "isBalance";

    /** 是否账平 — Excel 表头。 */
    String LABEL_IS_BALANCE = "是否账平";
}
