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

package com.openhis.health.application.inventory.pharmacy.stockin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogUnitBandDto;
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

    @Schema(description = "物品来源物理表名")
    private String productTableName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键")
    private Long productDefinitionId;

    @Schema(description = "目录业务编号 productBusNo")
    private String productBusNo;

    @Schema(description = "物品名称")
    private String productName;

    @Schema(description = "规格")
    private String specification;

    @Schema(description = "厂家合一")
    private String manufacturer;

    @Schema(description = "批号")
    private String lotNumber;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "效期")
    private LocalDateTime expirationDate;

    @Schema(description = "来源药库可用量")
    private BigDecimal availableQuantity;

    @Schema(description = "单位四槽")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    @Schema(description = "批号维进价展示单价（LOT_NUMBER_COST，随 lotNumber；无命中为 null）")
    private BigDecimal purchaseUnitPrice;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存行主键")
    private Long inventoryItemId;
}
