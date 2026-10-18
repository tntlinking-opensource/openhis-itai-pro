/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissue.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科室领用单详情明细行（IF-DEP-IS-03）。
 *
 * <p>与科室调出/请领明细列族同构；数量字段为领用量；单价遵循 BR-PRICE-SRC-001 仅展示落库槽价。
 * 前端明细网格列：项目名称、规格、厂家、数量、金额、批号、有效期、追溯码、备注。</p>
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

    /**
     * 批号效期止点（落库 {@code end_time}；前端「有效期」列回显）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "批号效期止点 endTime")
    private LocalDateTime endTime;

    /** 行备注 remake（库列 remake；前端「备注」列）。 */
    @Schema(description = "行备注 remake")
    private String remake;

    /** 产品目录 productCatalog。 */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
