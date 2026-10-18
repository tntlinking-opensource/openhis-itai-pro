/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissue.dto;

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
 * 科室领用单列表聚合行（IF-DEP-IS-02：每 bus_no 一行）。
 *
 * <p>状态等为持久化 {@code code}；{@code statusEnumText} 与展示名由列表查询后在应用层装配。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室领用单列表聚合行")
public class DeptIssueListRowDto {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 科室药房 purposeLocationId。 */
    @Schema(description = "科室药房 purposeLocationId")
    private String purposeLocationId;

    /** 科室药房名称 purposeLocationName。 */
    @Schema(description = "科室药房名称 purposeLocationName")
    private String purposeLocationName;

    /**
     * 领用科室行政 Organization 主键（库列 {@code consuming_organization_id}；与科室药房解耦）。
     */
    @Schema(description = "领用科室 consumingOrganizationId")
    private String consumingOrganizationId;

    /** 领用科室展示名（由 organization 主数据解析）。 */
    @Schema(description = "领用科室名称 consumingOrganizationName")
    private String consumingOrganizationName;

    /** 单据状态 SupplyStatus#getCode。 */
    @Schema(description = "单据状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 状态中文展示 statusEnumText。 */
    @Schema(description = "状态中文展示 statusEnumText")
    private String statusEnumText;

    /** 驳回原因（status=REJECT 时取供应行 reason；其它态可空）。 */
    @Schema(description = "驳回原因")
    private String rejectReason;

    /**
     * 整单金额合计（明细行进价合计；侧栏「总金额」读 {@code totalAmount}）。
     */
    @Schema(description = "整单金额合计 totalAmount")
    private BigDecimal totalAmount;

    /** 申请人 applicantId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "申请人 applicantId")
    private Long applicantId;

    /** 申请人姓名 applicantName。 */
    @Schema(description = "申请人姓名 applicantName")
    private String applicantName;

    /** 申请时间 applyTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;

    /** 审批人 approverId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批人姓名 approverName。 */
    @Schema(description = "审批人姓名 approverName")
    private String approverName;

    /** 审批时间 approvalTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;
}
