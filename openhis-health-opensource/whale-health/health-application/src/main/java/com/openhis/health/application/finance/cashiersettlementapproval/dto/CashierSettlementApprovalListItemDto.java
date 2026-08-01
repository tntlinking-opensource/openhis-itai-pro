/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.cashiersettlementapproval.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.financial.cashiersettlement.CashierSettlementPartyNamedRow;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交账审批列表行 DTO（IF-CS-APR-02）。
 *
 * <p>不使用 {@code @Accessors(chain=true)}，以保证 {@link CashierSettlementPartyNamedRow} 的 void setter 可实现。</p>
 */
@Data
@Schema(description = "交账审批列表行")
public class CashierSettlementApprovalListItemDto implements CashierSettlementPartyNamedRow {

    /** 交账头主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头主键")
    private Long settlementId;

    /** 交账单号。 */
    @Schema(description = "交账单号")
    private String busNo;

    /** 交账类型 code。 */
    @Schema(description = "交账类型 code")
    private String typeEnum;

    /** 交账类型展示名。 */
    @Schema(description = "交账类型展示名")
    private String typeEnumText;

    /** 审批状态 code。 */
    @Schema(description = "审批状态 code")
    private String statusEnum;

    /** 审批状态展示名。 */
    @Schema(description = "审批状态展示名")
    private String statusEnumText;

    /** 交账科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账科室主键")
    private Long orgId;

    /** 交账药房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账药房主键")
    private Long locationId;

    /** 交账药房名称。 */
    @Schema(description = "交账药房名称")
    private String locationName;

    /** 交账科室名称。 */
    @Schema(description = "交账科室名称")
    private String orgName;

    /** 业务窗起。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务窗起")
    private LocalDateTime dateFrom;

    /** 业务窗止。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务窗止")
    private LocalDateTime dateTo;

    /** 挂载笔数快照。 */
    @Schema(description = "挂载笔数快照")
    private Integer paymentCount;

    /** 列表金额快照。 */
    @Schema(description = "列表金额快照")
    private BigDecimal totalIncome;

    /** 提交人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "提交人主键")
    private Long submitterId;

    /** 交账人（提交人）姓名。 */
    @Schema(description = "交账人姓名")
    private String submitterName;

    /** 交账提交时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "交账提交时间")
    private LocalDateTime submittedAt;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 审批人姓名。 */
    @Schema(description = "审批人姓名")
    private String approverName;

    /** 审批时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间")
    private LocalDateTime approvedAt;
}
