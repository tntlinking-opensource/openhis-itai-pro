/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stocktake.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科室药房盘点单列表聚合行（IF-DEPT-ST-02：每 {@code bus_no} 一行）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "科室药房盘点单列表聚合行")
public class StocktakeListRowDto {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 单据状态 SupplyStatus#getCode。 */
    @Schema(description = "单据状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 状态中文展示。 */
    @Schema(description = "状态中文展示")
    private String statusEnumText;

    /** 驳回原因（status=REJECT 时取供应行 reason；其它态可空）。 */
    @Schema(description = "驳回原因")
    private String rejectReason;

    /** 盘点科室药房位置主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 盘点科室药房 purposeLocationId。 */
    @Schema(description = "盘点科室药房 purposeLocationId")
    private Long purposeLocationId;

    /** 盘点科室药房名称。 */
    @Schema(description = "盘点科室药房名称")
    private String purposeLocationName;

    /** 明细行数。 */
    @Schema(description = "明细行数")
    private Integer itemCount;

    /** 盈亏数量合计。 */
    @Schema(description = "盈亏数量合计")
    private BigDecimal profitLossQuantitySum;

    /** 实盘金额合计。 */
    @Schema(description = "实盘金额合计")
    private BigDecimal actualAmountSum;

    /** 申请人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 申请人 applicantId。 */
    @Schema(description = "申请人 applicantId")
    private Long applicantId;

    /** 申请人姓名。 */
    @Schema(description = "申请人姓名")
    private String applicantName;

    /** 申请时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 申请时间 applyTime。 */
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 审批人 approverId。 */
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批人姓名。 */
    @Schema(description = "审批人姓名")
    private String approverName;

    /** 审批时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 审批时间 approvalTime。 */
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /** 制单时间（明细 occurrence_time 组内最大值）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 制单时间 occurrenceTime。 */
    @Schema(description = "制单时间 occurrenceTime")
    private LocalDateTime occurrenceTime;
}
