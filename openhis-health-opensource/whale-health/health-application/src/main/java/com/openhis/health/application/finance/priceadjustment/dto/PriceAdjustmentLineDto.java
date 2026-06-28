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

package com.openhis.health.application.finance.priceadjustment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 调价项目行 DTO（对应主表一行 + 子表明细）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调价项目行")
public class PriceAdjustmentLineDto {

    /** 主行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主行主键")
    private Long id;

    /** 调价单号。 */
    @Schema(description = "调价单号")
    private String busNo;

    /** 项目物理表。 */
    @Schema(description = "项目物理表")
    private String itemTable;

    /** 项目物理表展示名。 */
    @Schema(description = "项目物理表展示名")
    private String itemTableText;

    /** 项目定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "项目定义主键")
    private Long itemId;

    /** 项目名称（目录 enrich）。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 院内项目编码（目录 enrich）。 */
    @Schema(description = "院内项目编码")
    private String itemCode;

    /** 规格（目录 enrich）。 */
    @Schema(description = "规格")
    private String specification;

    /** 厂家/产地（物流目录 enrich）。 */
    @Schema(description = "厂家/产地")
    private String manufacturer;

    /** 目标进价。 */
    @Schema(description = "目标进价")
    private BigDecimal newPurchaseAmount;

    /** 目标售价。 */
    @Schema(description = "目标售价")
    private BigDecimal newSellingAmount;

    /** 调价原因 code。 */
    @Schema(description = "调价原因 code")
    private String adjustmentReason;

    /** 调价原因展示名。 */
    @Schema(description = "调价原因展示名")
    private String adjustmentReasonText;

    /** 制单时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "制单时间")
    private Date adjustmentTime;

    /** 行状态 code。 */
    @Schema(description = "行状态 code")
    private String statusEnum;

    /** 行状态展示名。 */
    @Schema(description = "行状态展示名")
    private String statusEnumText;

    /** 操作人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "操作人主键")
    private Long operatorId;

    /** 机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "机构主键")
    private Long orgId;

    /** 展示口径：{@code clinical} / {@code logistics}。 */
    @Schema(description = "调价展示口径")
    private String adjustmentStyle;

    /** 展示口径文案。 */
    @Schema(description = "调价展示口径文案")
    private String adjustmentStyleText;

    /** 批号明细或诊疗调前价行。 */
    @Schema(description = "批号明细")
    private List<PriceAdjustmentDetailDto> details;
}
