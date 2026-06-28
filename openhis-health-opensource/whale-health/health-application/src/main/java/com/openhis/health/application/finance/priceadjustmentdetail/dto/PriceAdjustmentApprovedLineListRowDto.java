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

package com.openhis.health.application.finance.priceadjustmentdetail.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 调价明细列表行 DTO（已生效项目主行；**不含**调前/调后价，**BR-PRC-DTL-008**）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调价明细列表行")
public class PriceAdjustmentApprovedLineListRowDto {

    /** 主行主键 {@code cus_price_adjustment.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主行主键")
    private Long id;

    /** 调价单号 {@code bus_no}。 */
    @Schema(description = "调价单号")
    private String busNo;

    /** 生效时间 {@code approve_time}。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "生效时间")
    private Date approveTime;

    /** 项目物理表 code。 */
    @Schema(description = "项目物理表")
    private String itemTable;

    /** 项目类型展示名。 */
    @Schema(description = "项目类型展示名")
    private String itemTableText;

    /** 项目定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "项目定义主键")
    private Long itemId;

    /** 项目名称（目录 enrich）。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 院内编码（目录 enrich）。 */
    @Schema(description = "院内编码")
    private String itemCode;

    /** 展示口径 {@code logistics}/{@code clinical}。 */
    @Schema(description = "展示口径")
    private String adjustmentStyle;

    /** 展示口径文案。 */
    @Schema(description = "展示口径文案")
    private String adjustmentStyleText;

    /** 调价原因 code。 */
    @Schema(description = "调价原因 code")
    private String adjustmentReason;

    /** 调价原因展示名。 */
    @Schema(description = "调价原因展示名")
    private String adjustmentReasonText;

    /** 盈亏合计（固化 impact 之和，**BR-PRC-DTL-007**）。 */
    @Schema(description = "盈亏合计")
    private BigDecimal totalImpact;

    /** 制单人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "制单人主键")
    private Long operatorId;

    /** 制单人展示名。 */
    @Schema(description = "制单人展示名")
    private String operatorName;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 审批人展示名。 */
    @Schema(description = "审批人展示名")
    private String approverName;
}
