/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

/**
 * 调价单列表聚合行 DTO（按 {@code bus_no} 一行，对齐进货单列表）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调价单列表行")
public class PriceAdjustmentDocumentListRowDto {

    /** 调价单号。 */
    @Schema(description = "调价单号")
    private String busNo;

    /** 审批状态 code。 */
    @Schema(description = "审批状态 code")
    private String statusEnum;

    /** 审批状态展示名。 */
    @Schema(description = "审批状态展示名")
    private String statusEnumText;

    /** 项目物理表。 */
    @Schema(description = "项目物理表")
    private String itemTable;

    /** 调价展示口径。 */
    @Schema(description = "调价展示口径")
    private String adjustmentStyle;

    /** 机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "机构主键")
    private Long orgId;

    /** 制单人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "制单人主键")
    private Long operatorId;

    /** 制单人展示名。 */
    @Schema(description = "制单人展示名")
    private String operatorName;

    /** 提交人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "提交人主键")
    private Long submitterId;

    /** 提交人展示名。 */
    @Schema(description = "提交人展示名")
    private String submitterName;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 审批人展示名。 */
    @Schema(description = "审批人展示名")
    private String approverName;

    /** 制单时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "制单时间")
    private Date adjustmentTime;

    /** 申请时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "申请时间")
    private Date submitTime;

    /** 审批时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间")
    private Date approveTime;

    /** 进价盈亏合计。 */
    @Schema(description = "进价盈亏合计")
    private BigDecimal totalPurchaseImpact;

    /** 售价盈亏合计。 */
    @Schema(description = "售价盈亏合计")
    private BigDecimal totalSellingImpact;

    /** 项目行数。 */
    @Schema(description = "项目行数")
    private Integer lineCount;
}
