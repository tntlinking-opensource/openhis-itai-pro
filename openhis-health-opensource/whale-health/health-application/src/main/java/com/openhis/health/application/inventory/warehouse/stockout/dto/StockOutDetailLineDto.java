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

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
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
 *
 * <p>与药房入库详情列族对偶；枚举与字典列对外为持久化 {@code code} 及人读文案字段。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
/** 药库出库单详情明细行。 */
@Schema(description = "药库出库单详情明细行")
public class StockOutDetailLineDto {

    /** 供应请求行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应请求行主键")
    private Long id;

    /** 物品定义物理表名。 */
    @Schema(description = "物品来源表名")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 产品展示名称。 */
    @Schema(description = "物品名称")
    private String productName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String specification;

    /** 厂家合一展示。 */
    @Schema(description = "厂家合一展示")
    private String manufacturer;

    /** 行数量。 */
    @Schema(description = "数量")
    private BigDecimal itemQuantity;

    /** 计量单位持久化身份。 */
    @Schema(description = "计量单位编码")
    private String unitCode;

    /** 计量单位人读文案。 */
    @Schema(description = "计量单位展示名")
    private String unitCodeDictText;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 效期区间起点。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 效期起点。 */
    @Schema(description = "效期起点")
    private LocalDateTime startTime;

    /** 效期区间止点。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
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

    /** 业务发生时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 业务发生时间。 */
    @Schema(description = "业务发生时间")
    private LocalDateTime occurrenceTime;

    /** 多单位目录编辑提示；无读模型命中时可不下发。 */
    @Schema(description = "多单位目录提示")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
