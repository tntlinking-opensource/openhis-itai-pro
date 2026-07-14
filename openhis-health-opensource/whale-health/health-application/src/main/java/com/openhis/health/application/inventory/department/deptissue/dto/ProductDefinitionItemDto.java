/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 科室领用选品分页行（IF-DEP-IS-11）。
 *
 * <p>字段结构与科室调出选品 {@code ProductDefinitionItemDto} 同构。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室领用选品分页行")
public class ProductDefinitionItemDto {

    /** 物品来源物理表名 productTableName。 */
    @Schema(description = "物品来源物理表名 productTableName")
    private String productTableName;

    /** 产品定义主键 productDefinitionId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键 productDefinitionId")
    private Long productDefinitionId;

    /** 目录业务编号 productBusNo。 */
    @Schema(description = "目录业务编号 productBusNo")
    private String productBusNo;

    /** 物品名称 productName。 */
    @Schema(description = "物品名称 productName")
    private String productName;

    /** 规格 specification。 */
    @Schema(description = "规格 specification")
    private String specification;

    /** 厂家合一 manufacturer。 */
    @Schema(description = "厂家合一 manufacturer")
    private String manufacturer;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 效期 expirationDate。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "效期 expirationDate")
    private LocalDateTime expirationDate;

    /** 科室药房可用量 availableQuantity。 */
    @Schema(description = "科室药房可用量 availableQuantity")
    private BigDecimal availableQuantity;

    /** 单位四槽 unitBands。 */
    @Schema(description = "单位四槽 unitBands")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 批号维进价展示（LOT_NUMBER_COST，随 lotNumber）。 */
    @Schema(description = "批号维进价展示（LOT_NUMBER_COST，随 lotNumber）")
    private BigDecimal purchaseUnitPrice;

    /** 库存行主键 inventoryItemId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存行主键 inventoryItemId")
    private Long inventoryItemId;
}
