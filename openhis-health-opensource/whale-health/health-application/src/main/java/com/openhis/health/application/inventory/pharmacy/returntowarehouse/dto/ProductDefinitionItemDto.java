/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 产品定义选品行（本场景已废弃自由选品，仅保留历史接口兼容）。
 */
@Getter
@Setter
public class ProductDefinitionItemDto {

    /** 产品定义主键。 */
    private Long productDefinitionId;
    /** 产品定义来源表名。 */
    private String productTableName;
    /** 产品展示名称。 */
    private String productName;
    /** 商品名/通用名展示。 */
    private String merchandiseName;
    /** 采购/包装单位编码。 */
    private String unitCode;
    /** 拆零/最小单位编码。 */
    private String minUnitCode;
    /** 品类编码。 */
    private String categoryCode;
    /** 销售单价（当前价读路径）。 */
    private BigDecimal price;
    /** 采购单价（当前价读路径）。 */
    private BigDecimal purchasePrice;
    /** 生产厂家。 */
    private String manufacturer;
    /** 拼音检索码。 */
    private String pyStr;
    /** 五笔检索码。 */
    private String wbStr;
}
