/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustmentdetail.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
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

    /** 主行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主行主键")
    private Long id;

    /** 调价单号。 */
    @Schema(description = "调价单号")
    private String busNo;

    /** 生效时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "生效时间")
    private Date approveTime;

    /** 项目物理表。 */
    @Schema(description = "项目物理表")
    private String itemTable;

    /** 项目类型展示名。 */
    @Schema(description = "项目类型展示名")
    private String itemTableText;

    /** 项目定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "项目定义主键")
    private Long itemId;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 院内编码。 */
    @Schema(description = "院内编码")
    private String itemCode;

    /** 展示口径。 */
    @Schema(description = "展示口径")
    private String adjustmentStyle;

    /** 展示口径文案。 */
    @Schema(description = "展示口径文案")
    private String adjustmentStyleText;

    /** 调价原因（自由文案）。 */
    @Schema(description = "调价原因（自由文案）")
    private String adjustmentReason;

    /** 调价原因展示（同文案；历史 code 过渡映射）。 */
    @Schema(description = "调价原因展示")
    private String adjustmentReasonText;

    /** 盈亏合计。 */
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
