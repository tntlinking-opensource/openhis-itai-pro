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

package com.openhis.health.application.inventory.warehouse.transferin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 药房入库单详情明细行（IF-PHA-IN-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 药房入库单详情明细行。 */
@Schema(description = "药房入库单详情明细行")
public class TransferInDetailLineDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应请求行主键")
    private Long id;

    /** 物品来源表名。 */
    @Schema(description = "物品来源表名")
    private String itemTable;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 物品名称。 */
    @Schema(description = "物品名称")
    private String productName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String specification;

    /** 厂家合一展示。 */
    @Schema(description = "厂家合一展示")
    private String manufacturer;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal itemQuantity;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码")
    private String unitCode;

    /** 计量单位展示名。 */
    @Schema(description = "计量单位展示名")
    private String unitCodeDictText;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 效期起点。 */
    @Schema(description = "效期起点")
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 效期止点。 */
    @Schema(description = "效期止点")
    private LocalDateTime endTime;

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String remake;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal price;

    /** 行总价。 */
    @Schema(description = "行总价")
    private BigDecimal totalPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 业务发生时间。 */
    @Schema(description = "业务发生时间")
    private LocalDateTime occurrenceTime;

    /** 多单位目录提示。 */
    @Schema(description = "多单位目录提示")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
