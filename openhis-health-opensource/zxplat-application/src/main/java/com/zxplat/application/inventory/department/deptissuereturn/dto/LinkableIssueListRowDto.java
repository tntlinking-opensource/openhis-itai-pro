/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissuereturn.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
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

    /**
     * 科室药房展示名：由位置主数据解析，供选单「科室药房」列直接展示。
     */
    @Schema(description = "科室药房名称 purposeLocationName")
    private String purposeLocationName;

    /** 领用科室展示名（优先落库 id 解析，历史单回落药房上级科室）。 */
    @Schema(description = "领用科室名称 consumingOrganizationName")
    private String consumingOrganizationName;

    /**
     * 制单人（申请人）主键：取领用单头行 {@code applicantId}。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "制单人主键 applicantId")
    private Long applicantId;

    /**
     * 制单人展示名：由申请人主键解析。
     */
    @Schema(description = "制单人名称 creatorName")
    private String creatorName;

    /** 总金额 totalAmount。 */
    @Schema(description = "总金额 totalAmount")
    private BigDecimal totalAmount;

    /** 审批时间 approvalTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /**
     * 制单时间：优先头行 {@code occurrenceTime}，其次 {@code applyTime}，最后回落 {@code approvalTime}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "制单时间 applyTime")
    private LocalDateTime applyTime;

    /** 是否仍可退领 returnable。 */
    @Schema(description = "是否仍可退领 returnable")
    private Boolean returnable;
}
