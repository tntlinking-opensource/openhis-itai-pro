/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stocktake.response;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.department.stocktake.dto.StocktakeDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点单详情出参（IF-DEPT-ST-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "盘点单详情 IF-DEPT-ST-03")
public class GetStocktakeDetailResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 状态 statusEnum。 */
    @Schema(description = "状态 statusEnum")
    private String statusEnum;

    /** 盘点科室药房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 盘点科室药房 purposeLocationId。 */
    @Schema(description = "盘点科室药房 purposeLocationId")
    private Long purposeLocationId;

    /** 盘点科室药房名称 purposeLocationName。 */
    @Schema(description = "盘点科室药房名称 purposeLocationName")
    private String purposeLocationName;

    /** 经手人。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /** 申请人。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 申请人 applicantId。 */
    @Schema(description = "申请人 applicantId")
    private Long applicantId;

    /** 申请时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 申请时间 applyTime。 */
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;

    /** 审批人。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 审批人 approverId。 */
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 审批时间 approvalTime。 */
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines")
    private List<StocktakeDetailLineDto> lines;
}
