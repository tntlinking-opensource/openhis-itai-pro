/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.summarydispense.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 待核发汇总单头（IF-IPS-01/02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "汇总单头")
public class InpatientPharmacySummarySlipHeaderDto {

    /** 汇总单号（与 {@code bus_no} 同义）。 */
    @Schema(description = "汇总单号")
    private String busNo;

    /** 护士站发送时间。 */
    @Schema(description = "申请时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime applyTime;

    /** 药房核发时间（未核发为空）。 */
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

    /** 申请人（护士）主键。 */
    @Schema(description = "申请人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;

    /** 申请人姓名。 */
    @Schema(description = "申请人姓名")
    private String applicantName;

    /** 领药人主键（核发后才有值）。 */
    @Schema(description = "领药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;

    /** 领药人姓名。 */
    @Schema(description = "领药人姓名")
    private String receiverName;

    /** Supply 审批态 code（中转展示，不作已发药门控）。 */
    @Schema(description = "审批态 code")
    private String supplyStatusEnum;

    /** 审批态展示（Supply 中转态，仅供排障）。 */
    @Schema(description = "Supply 审批态展示")
    private String supplyStatusEnumText;

    /** 是否已发药：仅以同号发放行全部为 COMPLETED 为准（BR-IPS-013）。 */
    @Schema(description = "是否已发药（发放表终态）")
    private Boolean summaryDispenseCompleted;

    /** 汇总发药门控展示：待核对 / 已发药。 */
    @Schema(description = "汇总发药门控展示")
    private String summaryDispenseStatusText;
}
