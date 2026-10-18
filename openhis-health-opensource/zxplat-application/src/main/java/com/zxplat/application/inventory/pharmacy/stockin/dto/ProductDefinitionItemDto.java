/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.stockin.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 药房入库选品分页行（IF-PHA-IN-11）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药房入库选品分页行")
public class ProductDefinitionItemDto {

    /** 物品来源物理表名。 */
    @Schema(description = "物品来源物理表名")
    private String productTableName;

    /** 产品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键")
    private Long productDefinitionId;

    /** 目录业务编号 productBusNo。 */
    @Schema(description = "目录业务编号 productBusNo")
    private String productBusNo;

    /** 物品名称。 */
    @Schema(description = "物品名称")
    private String productName;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 厂家合一。 */
    @Schema(description = "厂家合一")
    private String manufacturer;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 效期。 */
    @Schema(description = "效期")
    private LocalDateTime expirationDate;

    /** 来源药库可用量。 */
    @Schema(description = "来源药库可用量")
    private BigDecimal availableQuantity;

    /** 单位四槽。 */
    @Schema(description = "单位四槽")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 批号维进价展示单价（LOT_NUMBER_COST，随 lotNumber；无命中为 null）。 */
    @Schema(description = "批号维进价展示单价（LOT_NUMBER_COST，随 lotNumber；无命中为 null）")
    private BigDecimal purchaseUnitPrice;

    /** 库存行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存行主键")
    private Long inventoryItemId;
}
