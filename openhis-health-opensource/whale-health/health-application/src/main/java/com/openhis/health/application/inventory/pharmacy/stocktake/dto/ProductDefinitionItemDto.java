/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.pharmacy.stocktake.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Accessors(chain = true)
/** 盘点选品行。 */
@Schema(description = "盘点选品行")
public class ProductDefinitionItemDto {

    /** 物品定义物理表名。 */
    @Schema(description = "产品表 productTableName")
    private String productTableName;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 产品主键 productDefinitionId。 */
    @Schema(description = "产品主键 productDefinitionId")
    private Long productDefinitionId;

    /** 目录业务流水号（药品/耗材/器材定义 bus_no）。 */
    @Schema(description = "目录业务编号 productBusNo")
    private String productBusNo;

    /** 产品名称。 */
    @Schema(description = "产品名称 productName")
    private String productName;

    /** 规格。 */
    @Schema(description = "规格 specification")
    private String specification;

    /** 厂家。 */
    @Schema(description = "厂家 manufacturer")
    private String manufacturer;

    /** 批号。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 效期。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 效期 expirationDate。 */
    @Schema(description = "效期 expirationDate")
    private LocalDateTime expirationDate;

    /** 账面/可用量（purposeLocationId 下）。 */
    @Schema(description = "可用量/账面量 availableQuantity")
    private BigDecimal availableQuantity;

    /** 单位槽。 */
    @Schema(description = "单位槽 unitBands")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 批号维进价（LOT_NUMBER_COST 展示辅助）。 */
    @Schema(description = "采购单价 purchaseUnitPrice")
    private BigDecimal purchaseUnitPrice;

    /** 库存行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库存项主键 inventoryItemId。 */
    @Schema(description = "库存项主键 inventoryItemId")
    private Long inventoryItemId;
}
