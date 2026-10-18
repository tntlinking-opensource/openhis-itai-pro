/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stocktake.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药库盘点单详情明细行（IF-WHS-ST-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药库盘点单详情明细行")
public class StocktakeDetailLineDto {

    /** 明细行主键 lineId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "明细行主键 lineId")
    private Long lineId;

    /** 物品来源表名 itemTable。 */
    @Schema(description = "物品来源表名 itemTable")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键 itemId。 */
    @Schema(description = "物品定义主键 itemId")
    private Long itemId;

    /** 物品名称 productName。 */
    @Schema(description = "物品名称 productName")
    private String productName;

    /** 规格 specification。 */
    @Schema(description = "规格 specification")
    private String specification;

    /** 厂家 manufacturer。 */
    @Schema(description = "厂家 manufacturer")
    private String manufacturer;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 剩余追溯码 traceNo。 */
    @Schema(description = "剩余追溯码 traceNo")
    private String traceNo;

    /** 效期止点。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 效期 expirationDate。 */
    @Schema(description = "效期 expirationDate")
    private LocalDateTime expirationDate;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 账面数量 bookQuantity。 */
    @Schema(description = "账面数量 bookQuantity")
    private BigDecimal bookQuantity;

    /** 盈亏数量 profitLossQuantity。 */
    @Schema(description = "盈亏数量 profitLossQuantity")
    private BigDecimal profitLossQuantity;

    /** 实盘数量 actualQuantity。 */
    @Schema(description = "实盘数量 actualQuantity")
    private BigDecimal actualQuantity;

    /** 实盘金额 actualAmount。 */
    @Schema(description = "实盘金额 actualAmount")
    private BigDecimal actualAmount;

    /** 盈亏金额 profitLossAmount。 */
    @Schema(description = "盈亏金额 profitLossAmount")
    private BigDecimal profitLossAmount;

    /** 单价 price。 */
    @Schema(description = "单价 price")
    private BigDecimal price;

    /** 备注 remake。 */
    @Schema(description = "备注 remake")
    private String remake;

    /** 业务发生时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 业务发生时间 occurrenceTime。 */
    @Schema(description = "业务发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;

    /** 产品目录 productCatalog。 */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
