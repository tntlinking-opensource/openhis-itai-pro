/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可关联领用单列表行（IF-DEP-TL-12，按 bus_no 聚合）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "可关联领用单列表行")
public class LinkableIssueListRowDto {

    /** 领用单号 busNo。 */
    @Schema(description = "领用单号 busNo")
    private String busNo;

    /** 科室药房 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室药房 purposeLocationId")
    private Long purposeLocationId;

    /** 领用科室名称 consumingOrganizationName。 */
    @Schema(description = "领用科室名称 consumingOrganizationName")
    private String consumingOrganizationName;

    /** 总金额 totalAmount。 */
    @Schema(description = "总金额 totalAmount")
    private BigDecimal totalAmount;

    /** 审批时间 approvalTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /** 是否仍可退领 returnable。 */
    @Schema(description = "是否仍可退领 returnable")
    private Boolean returnable;
}
