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

package com.openhis.health.application.inventory.warehouse.stockout.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.enums.PriceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@Accessors(chain = true)
/** 出库选品分页行。 */
@Schema(description = "出库选品分页行")
public class ProductDefinitionItemDto {

    /** 产品定义物理表名，与库存物品表一致。 */
    @Schema(description = "物品来源物理表名")
    private String productTableName;

    /** 产品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 产品定义主键。 */
    @Schema(description = "产品定义主键")
    private Long productDefinitionId;

    /** 目录业务流水号（药品/耗材/器材定义 bus_no）。 */
    @Schema(description = "目录业务编号 productBusNo")
    private String productBusNo;

    /** 通用名或品名展示。 */
    @Schema(description = "物品名称")
    private String productName;

    /** 规格展示。 */
    @Schema(description = "规格")
    private String specification;

    /** 厂家信息合一展示。 */
    @Schema(description = "厂家合一")
    private String manufacturer;

    /** 库存行批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 该批效期（展示用时间点）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 效期。 */
    @Schema(description = "效期")
    private LocalDateTime expirationDate;

    /** 源库可用量（与库存读模型 quantity 口径一致时为拆零单位）。 */
    @Schema(description = "源库可用量")
    private BigDecimal availableQuantity;

    /** 目录多单位槽位，供前端选单位。 */
    @Schema(description = "单位四槽")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 批号维进价展示（定价表 {@link PriceType#LOT_NUMBER_COST}，随 {@link #lotNumber}）；无批号或无命中时为 {@code null}。 */
    @Schema(description = "批号维进价展示（LOT_NUMBER_COST，随 lotNumber）")
    private BigDecimal purchaseUnitPrice;

    /** 库存项主键，用于出库行回写引用。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库存行主键。 */
    @Schema(description = "库存行主键")
    private Long inventoryItemId;
}
