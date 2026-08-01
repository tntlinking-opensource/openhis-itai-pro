/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.gainloss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药房损益单详情明细行（IF-PHA-GL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药房损益单详情明细行")
public class PharmacyGainLossDetailLineDto {

    /** 明细行主键 lineId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "明细行主键 lineId")
    private Long lineId;

    /** 物品来源表名 itemTable。 */
    @Schema(description = "物品来源表名 itemTable")
    private String itemTable;

    /** 物品定义主键 itemId。 */
    @JsonSerialize(using = ToStringSerializer.class)
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

    /** 损益扫码 traceNo（报溢 IN / 报损 OUT，与盈亏正负同向）。 */
    @Schema(description = "损益扫码 traceNo（与盈亏正负同向直接加减）")
    private String traceNo;
    /** 效期 expirationDate。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "效期 expirationDate")
    private LocalDateTime expirationDate;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 账面数量 bookQuantity。 */
    @Schema(description = "账面数量 bookQuantity")
    private BigDecimal bookQuantity;

    /** 盈亏数量 gainLossQuantity。 */
    @Schema(description = "盈亏数量 gainLossQuantity")
    private BigDecimal gainLossQuantity;

    /** 实盘数量 actualQuantity。 */
    @Schema(description = "实盘数量 actualQuantity")
    private BigDecimal actualQuantity;

    /** 实盘金额 actualAmount。 */
    @Schema(description = "实盘金额 actualAmount")
    private BigDecimal actualAmount;

    /** 盈亏金额 gainLossAmount。 */
    @Schema(description = "盈亏金额 gainLossAmount")
    private BigDecimal gainLossAmount;

    /** 单价 price。 */
    @Schema(description = "单价 price")
    private BigDecimal price;

    /** 变更原因 reason。 */
    @Schema(description = "变更原因 reason")
    private String reason;

    /** 备注 remake。 */
    @Schema(description = "备注 remake")
    private String remake;

    /** 业务发生时间 occurrenceTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;

    /** 产品目录 productCatalog。 */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
