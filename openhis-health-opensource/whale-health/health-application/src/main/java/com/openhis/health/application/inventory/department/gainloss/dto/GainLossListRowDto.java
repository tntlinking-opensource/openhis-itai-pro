/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.gainloss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科室药房损益单列表聚合行（IF-DEPT-GL-02：每 {@code bus_no} 一行）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "科室药房损益单列表聚合行")
public class GainLossListRowDto {

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

    /** 损益科室药房 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "损益科室药房 purposeLocationId")
    private Long purposeLocationId;

    /** 损益科室药房名称。 */
    @Schema(description = "损益科室药房名称")
    private String purposeLocationName;

    /** 明细行数。 */
    @Schema(description = "明细行数")
    private Integer itemCount;

    /** 盈亏数量合计。 */
    @Schema(description = "盈亏数量合计")
    private BigDecimal gainLossQuantitySum;

    /** 实盘金额合计。 */
    @Schema(description = "实盘金额合计")
    private BigDecimal actualAmountSum;

    /** 申请人 applicantId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "申请人 applicantId")
    private Long applicantId;

    /** 申请人姓名。 */
    @Schema(description = "申请人姓名")
    private String applicantName;

    /** 申请时间 applyTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;

    /** 审批人 approverId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批人姓名。 */
    @Schema(description = "审批人姓名")
    private String approverName;

    /** 审批时间 approvalTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /** 制单时间 occurrenceTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "制单时间 occurrenceTime")
    private LocalDateTime occurrenceTime;
}
