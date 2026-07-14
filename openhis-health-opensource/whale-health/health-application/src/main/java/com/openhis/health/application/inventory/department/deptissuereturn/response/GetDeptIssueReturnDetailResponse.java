/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.department.deptissuereturn.dto.DeptIssueReturnDetailLineDto;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 科室退领单详情响应（IF-DEP-TL-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室退领单详情 IF-DEP-TL-03")
public class GetDeptIssueReturnDetailResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 源领用单号 originalBusNo。 */
    @Schema(description = "源领用单号 originalBusNo")
    private String originalBusNo;

    /** 状态 statusEnum。 */
    @Schema(description = "状态 statusEnum")
    private String statusEnum;

    /** 科室药房 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室药房 purposeLocationId")
    private Long purposeLocationId;

    /** 科室药房名称 purposeLocationName。 */
    @Schema(description = "科室药房名称 purposeLocationName")
    private String purposeLocationName;

    /** 领用科室 consumingOrganizationId。 */
    @Schema(description = "领用科室 consumingOrganizationId")
    private String consumingOrganizationId;

    /** 领用科室名称 consumingOrganizationName。 */
    @Schema(description = "领用科室名称 consumingOrganizationName")
    private String consumingOrganizationName;

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
    private List<DeptIssueReturnDetailLineDto> lines;
}
