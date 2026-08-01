/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.transferin.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科室调入单列表聚合行（IF-DEPT-TI-02：每 bus_no 一行，突出来源位置「从哪入」）。
 *
 * <p>类型、状态等为持久化 {@code code}（B.10.8.1）；{@code statusEnumText} 与来源展示名由列表查询后在应用层装配。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室调入单列表聚合行")
public class TransferInListRowDto {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 供应类型持久化码，固定为 DEPT_TRANSFER_IN。 */
    @Schema(description = "供应类型持久化码，固定为 DEPT_TRANSFER_IN")
    private String typeEnum;

    /** 单据状态持久化码 SupplyStatus#getCode。 */
    @Schema(description = "单据状态持久化码 SupplyStatus#getCode")
    private String statusEnum;

    /** 单据状态中文展示（SupplyStatus#getDisplay）。 */
    @Schema(description = "单据状态中文展示（SupplyStatus#getDisplay）")
    private String statusEnumText;

    /** 驳回原因（status=REJECT 时取供应行 reason；其它态可空）。 */
    @Schema(description = "驳回原因")
    private String rejectReason;

    /** 来源药库主键（从哪入）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源药库主键（从哪入）")
    private Long sourceLocationId;

    /** 来源位置展示名。 */
    @Schema(description = "来源位置展示名")
    private String sourceLocationName;

    /** 目的科室药房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的科室药房主键")
    private Long purposeLocationId;

    /** 申请时间。 */
    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    /** 审批时间。 */
    @Schema(description = "审批时间")
    private LocalDateTime approvalTime;

    /** 申请人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "申请人主键")
    private Long applicantId;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 制单人展示名。 */
    @Schema(description = "制单人展示名")
    private String creatorName;

    /** 审批人展示名。 */
    @Schema(description = "审批人展示名")
    private String approverName;

    /** 单据总金额（同号各行 total_price 之和）。 */
    @Schema(description = "单据总金额（同号各行 total_price 之和）")
    private BigDecimal totalAmount;

    /** 关联上游业务单号（系统生成时展示调出单号）。 */
    @Schema(description = "关联上游业务单号（系统生成时展示调出单号）")
    private String originalBusNo;
}
