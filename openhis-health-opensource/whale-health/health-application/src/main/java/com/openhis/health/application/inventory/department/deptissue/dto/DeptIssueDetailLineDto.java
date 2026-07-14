/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 科室领用单详情明细行（IF-DEP-IS-03）。
 *
 * <p>与科室调出详情列族同构；数量字段为领用量；单价遵循 BR-PRICE-SRC-001 仅展示落库槽价。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室领用单详情明细行")
public class DeptIssueDetailLineDto {

    /** 物品来源表名 itemTable。 */
    @Schema(description = "物品来源表名 itemTable")
    private String itemTable;

    /** 物品定义主键 itemId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品定义主键 itemId")
    private Long itemId;

    /** 物品名称 itemName。 */
    @Schema(description = "物品名称 itemName")
    private String itemName;

    /** 规格 spec。 */
    @Schema(description = "规格 spec")
    private String spec;

    /** 厂家 manufacturer。 */
    @Schema(description = "厂家 manufacturer")
    private String manufacturer;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 追溯码 traceNo。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;

    /** 领用数量 itemQuantity。 */
    @Schema(description = "领用数量 itemQuantity")
    private BigDecimal itemQuantity;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 计量单位展示名 unitName。 */
    @Schema(description = "计量单位展示名 unitName")
    private String unitName;

    /** 单价 price。 */
    @Schema(description = "单价 price")
    private BigDecimal price;

    /** 行总价 totalPrice。 */
    @Schema(description = "行总价 totalPrice")
    private BigDecimal totalPrice;

    /** 产品目录 productCatalog。 */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
