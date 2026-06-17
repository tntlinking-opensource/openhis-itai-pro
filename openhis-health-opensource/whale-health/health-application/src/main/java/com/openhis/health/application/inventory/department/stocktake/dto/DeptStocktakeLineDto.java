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

package com.openhis.health.application.inventory.department.stocktake.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 科室盘点单写模型明细行（IF-DEP-ST-04/05）。
 *
 * <p>{@code profitLossQuantity} 映射持久化 {@code item_quantity}（盈亏，可负可零）；{@code actualQuantity} 映射 {@code total_quantity}。</p>
 */
@Data
@Accessors(chain = true)
/** 科室盘点单明细行。 */
@Schema(description = "科室盘点单明细行（写）")
public class DeptStocktakeLineDto {

    @Schema(description = "物品来源表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    /** 盈亏数量（拆零；盈为正、亏为负）。 */
    @Schema(description = "盈亏数量 profitLossQuantity", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal profitLossQuantity;

    /** 实盘数量（拆零，≥0）。 */
    @Schema(description = "实盘数量 actualQuantity")
    private BigDecimal actualQuantity;

    /** 实盘金额。 */
    @Schema(description = "实盘金额 actualAmount")
    private BigDecimal actualAmount;

    /** 盈亏金额（盈正亏负）。 */
    @Schema(description = "盈亏金额 profitLossAmount")
    private BigDecimal profitLossAmount;

    /** 计量单位持久化编码。 */
    @Schema(description = "计量单位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 选品锚点库存行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库存项主键 inventoryItemId。 */
    @Schema(description = "库存项主键 inventoryItemId（选品，可选）")
    private Long inventoryItemId;

    /** 行单价（展示/计价辅助）。 */
    @Schema(description = "单价")
    private BigDecimal price;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String remake;

    /** 业务发生时间（制单时间）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 业务发生时间 occurrenceTime。 */
    @Schema(description = "业务发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;
}
