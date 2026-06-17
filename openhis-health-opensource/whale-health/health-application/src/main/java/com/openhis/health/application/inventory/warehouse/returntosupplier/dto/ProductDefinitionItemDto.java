/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.dto;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class ProductDefinitionItemDto {

    /** 产品定义主键。 */
    @Schema(description = "产品定义 productDefinitionId")
    private Long productDefinitionId;
    /** 物品表名。 */
    @Schema(description = "物品表 productTableName")
    private String productTableName;
    /** 产品名称。 */
    @Schema(description = "产品名称 productName")
    private String productName;
    /** 商品名。 */
    @Schema(description = "商品名 merchandiseName")
    private String merchandiseName;
    /** 计量单位编码。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;
    /** 最小包装单位编码。 */
    @Schema(description = "拆零单位 minUnitCode")
    private String minUnitCode;
    /** 物资分类编码。 */
    @Schema(description = "分类编码 categoryCode")
    private String categoryCode;
    /** 单价。 */
    @Schema(description = "单价 price")
    private BigDecimal price;
    /** 采购进价。 */
    @Schema(description = "采购进价 purchasePrice")
    private BigDecimal purchasePrice;
    /** 生产厂家。 */
    @Schema(description = "生产厂家 manufacturer")
    private String manufacturer;
    /** 拼音检索码。 */
    @Schema(description = "拼音 pyStr")
    private String pyStr;
    /** 五笔检索码。 */
    @Schema(description = "五笔 wbStr")
    private String wbStr;
}
