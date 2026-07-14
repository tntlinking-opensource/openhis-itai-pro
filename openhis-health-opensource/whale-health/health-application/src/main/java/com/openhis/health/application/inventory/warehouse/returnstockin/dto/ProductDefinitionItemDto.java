/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 产品定义选品行（IF-WHS-RSI-11 历史出参，壳单禁止自由选品，接口固定拒绝 WHS_RSI_006）。
 *
 * <p>{@code purchasePrice} 为选品场景当前 ACTIVE 采购价 enrich；退库入库详情勿复用此 DTO。</p>
 */
@Getter
@Setter
@Schema(description = "退库入库选品行（已废弃接口出参）")
public class ProductDefinitionItemDto {

    /** 产品定义 productDefinitionId。 */
    @Schema(description = "产品定义 productDefinitionId")
    private Long productDefinitionId;

    /** 物品表 productTableName。 */
    @Schema(description = "物品表 productTableName")
    private String productTableName;

    /** 产品名称 productName。 */
    @Schema(description = "产品名称 productName")
    private String productName;

    /** 商品名 merchandiseName。 */
    @Schema(description = "商品名 merchandiseName")
    private String merchandiseName;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 拆零单位 minUnitCode。 */
    @Schema(description = "拆零单位 minUnitCode")
    private String minUnitCode;

    /** 分类编码 categoryCode。 */
    @Schema(description = "分类编码 categoryCode")
    private String categoryCode;

    /** 单价 price。 */
    @Schema(description = "单价 price")
    private BigDecimal price;

    /** 采购进价 purchasePrice。 */
    @Schema(description = "采购进价 purchasePrice")
    private BigDecimal purchasePrice;

    /** 生产厂家 manufacturer。 */
    @Schema(description = "生产厂家 manufacturer")
    private String manufacturer;

    /** 拼音 pyStr。 */
    @Schema(description = "拼音 pyStr")
    private String pyStr;

    /** 五笔 wbStr。 */
    @Schema(description = "五笔 wbStr")
    private String wbStr;
}
