/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stocktake.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点选品分页行（IF-DEPT-ST-11）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "盘点选品行")
public class ProductDefinitionItemDto {

    /** 产品表 productTableName。 */
    @Schema(description = "产品表 productTableName")
    private String productTableName;

    /** 产品主键 productDefinitionId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品主键 productDefinitionId")
    private Long productDefinitionId;

    /** 目录业务编号 productBusNo。 */
    @Schema(description = "目录业务编号 productBusNo")
    private String productBusNo;

    /** 产品名称 productName。 */
    @Schema(description = "产品名称 productName")
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

    /** 效期 expirationDate。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "效期 expirationDate")
    private LocalDateTime expirationDate;

    /** 可用量/账面量 availableQuantity。 */
    @Schema(description = "可用量/账面量 availableQuantity")
    private BigDecimal availableQuantity;

    /** 单位槽 unitBands。 */
    @Schema(description = "单位槽 unitBands")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 采购单价 purchaseUnitPrice。 */
    @Schema(description = "采购单价 purchaseUnitPrice")
    private BigDecimal purchaseUnitPrice;

    /** 库存项主键 inventoryItemId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存项主键 inventoryItemId")
    private Long inventoryItemId;
}
