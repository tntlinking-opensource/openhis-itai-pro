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

package com.openhis.health.application.inventory.pharmacy.gainloss.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 药房盘点单详情明细行（IF-PHA-ST-03）。
 */
@Data
@Accessors(chain = true)
/** 药房盘点单详情明细行。 */
@Schema(description = "药房盘点单详情明细行")
public class PharmacyGainLossDetailLineDto {

    /** 供应请求行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "明细行主键 lineId")
    private Long lineId;

    /** 物品定义物理表名。 */
    @Schema(description = "物品来源表名 itemTable")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键 itemId。 */
    @Schema(description = "物品定义主键 itemId")
    private Long itemId;

    /** 产品展示名称。 */
    @Schema(description = "物品名称 productName")
    private String productName;

    /** 规格展示。 */
    @Schema(description = "规格 specification")
    private String specification;

    /** 厂家展示。 */
    @Schema(description = "厂家 manufacturer")
    private String manufacturer;

    /** 批号。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 效期止点。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 效期 expirationDate。 */
    @Schema(description = "效期 expirationDate")
    private LocalDateTime expirationDate;

    /** 计量单位编码。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 账面数量（实时可用量展示）。 */
    @Schema(description = "账面数量 bookQuantity")
    private BigDecimal bookQuantity;

    /** 盈亏数量（持久化 item_quantity）。 */
    @Schema(description = "盈亏数量 gainLossQuantity")
    private BigDecimal gainLossQuantity;

    /** 实盘数量（持久化 total_quantity）。 */
    @Schema(description = "实盘数量 actualQuantity")
    private BigDecimal actualQuantity;

    /** 实盘金额（持久化 total_price）。 */
    @Schema(description = "实盘金额 actualAmount")
    private BigDecimal actualAmount;

    /** 盈亏金额（持久化 profit_loss_price）。 */
    @Schema(description = "盈亏金额 gainLossAmount")
    private BigDecimal gainLossAmount;

    /** 行单价。 */
    @Schema(description = "单价 price")
    private BigDecimal price;

    /** 行备注。 */
    @Schema(description = "备注 remake")
    private String remake;

    /** 业务发生时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 业务发生时间 occurrenceTime。 */
    @Schema(description = "业务发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;

    /** 多单位目录提示。 */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
