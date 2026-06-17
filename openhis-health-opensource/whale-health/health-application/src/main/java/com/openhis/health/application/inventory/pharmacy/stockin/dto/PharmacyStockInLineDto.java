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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 药房入库单写模型明细行（IF-PHA-IN-04/05）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 药房入库单明细行。 */
@Schema(description = "药房入库单明细行（写）")
public class PharmacyStockInLineDto {

    @Schema(description = "物品来源表名")
    private String itemTable;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键")
    private Long itemId;

    /**
     * 来源药库中已选定的 {@code wkf_inventory_item.id}。
     * <p>前端从选品带回时，草稿校验走「行级可用量」路径，与药库出库一致。</p>
     * <p>可空：未绑定时后端按「物品 + 归一化批号」在内存聚合后与可用目录行比对。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源药库库存行主键；有值时按行校验可用量，无值时按品+批号聚合")
    private Long inventoryItemId;

    @Schema(description = "数量")
    private BigDecimal itemQuantity;

    @Schema(description = "计量单位编码")
    private String unitCode;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal price;

    /** 行总价。 */
    @Schema(description = "行总价")
    private BigDecimal totalPrice;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String remake;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 业务发生时间。 */
    @Schema(description = "业务发生时间")
    private LocalDateTime occurrenceTime;
}
