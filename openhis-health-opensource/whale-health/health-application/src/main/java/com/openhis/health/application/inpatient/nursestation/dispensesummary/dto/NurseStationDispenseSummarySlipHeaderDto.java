/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 汇总单头（IF-IP-SUM-02/03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总单头")
public class NurseStationDispenseSummarySlipHeaderDto {

    @Schema(description = "汇总单号")
    /** 汇总单号。 */
    private String busNo;

    @Schema(description = "申请时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 申请时间。 */
    private LocalDateTime applyTime;

    /** 发药时间。 */
    @Schema(description = "发药时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime dispenseTime;

    /** 发放药房主键。 */
    @Schema(description = "发放药房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long purposeLocationId;

    /** 发放药房名称。 */
    @Schema(description = "发放药房名称")
    private String purposeLocationName;

    /** 申请人主键。 */
    @Schema(description = "申请人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;

    /** 申请人姓名。 */
    @Schema(description = "申请人姓名")
    private String applicantName;

    /** 领药人主键。 */
    @Schema(description = "领药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;

    /** 领药人姓名。 */
    @Schema(description = "领药人姓名")
    private String receiverName;

    /** 审批态 code。 */
    @Schema(description = "审批态 code")
    private String supplyStatusEnum;

    /** 审批态展示。 */
    @Schema(description = "审批态展示")
    private String supplyStatusEnumText;

    /** 发放态 code。 */
    @Schema(description = "发放态 code")
    private String deliveryStatusEnum;

    /** 发放态展示。 */
    @Schema(description = "发放态展示")
    private String deliveryStatusEnumText;
}
