/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.gainloss.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inventory.pharmacy.gainloss.dto.PharmacyGainLossDetailLineDto;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 损益单详情出参（IF-PHA-GL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "损益单详情 IF-PHA-GL-03")
public class GetPharmacyGainLossDetailResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 状态 statusEnum。 */
    @Schema(description = "状态 statusEnum")
    private String statusEnum;

    /** 损益药房 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "损益药房 purposeLocationId")
    private Long purposeLocationId;

    /** 损益药房名称 purposeLocationName。 */
    @Schema(description = "损益药房名称 purposeLocationName")
    private String purposeLocationName;

    /** 经手人 practitionerId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /** 申请人 applicantId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "申请人 applicantId")
    private Long applicantId;

    /** 申请时间 applyTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;

    /** 审批人 approverId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批时间 approvalTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines")
    private List<PharmacyGainLossDetailLineDto> lines;
}
