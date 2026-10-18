/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.dailysettlement.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.financial.cashiersettlement.CashierSettlementPartyNamedRow;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院交账历史行。
 *
 * <p>不使用 {@code @Accessors(chain=true)}，以保证 {@link CashierSettlementPartyNamedRow} 的 void setter 可实现。</p>
 */
@Data
@Schema(description = "住院交账历史行")
public class InpatientCashierSettlementHistoryItemDto implements CashierSettlementPartyNamedRow {

    /** 交账头主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头主键")
    private Long settlementId;

    /** 交账单号。 */
    @Schema(description = "交账单号")
    private String busNo;

    /** 交账提交时刻。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "交账提交时刻")
    private LocalDateTime submittedAt;

    /** 业务窗起。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务窗起")
    private LocalDateTime dateFrom;

    /** 业务窗止。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务窗止")
    private LocalDateTime dateTo;

    /** 本批人员快照。 */
    @Schema(description = "本批人员快照")
    private String practitionerIds;

    /** 提交人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "提交人主键")
    private Long submitterId;

    /** 交账人姓名。 */
    @Schema(description = "交账人姓名")
    private String submitterName;

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

    /** 交账科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账科室主键")
    private Long orgId;

    /** 交账科室名称。 */
    @Schema(description = "交账科室名称")
    private String orgName;

    /** 交账药房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账药房主键")
    private Long locationId;

    /** 交账药房名称。 */
    @Schema(description = "交账药房名称")
    private String locationName;

    /** 挂载笔数。 */
    @Schema(description = "挂载笔数")
    private Integer paymentCount;

    /** 列表金额快照。 */
    @Schema(description = "列表金额快照")
    private BigDecimal totalIncome;

    /** 是否可按挂载现算重打。 */
    @Schema(description = "是否可按挂载现算重打")
    private Boolean canReprintFromMounts;

    /** 交账状态 code。 */
    @Schema(description = "交账状态")
    private String statusEnum;

    /** 交账状态展示名。 */
    @Schema(description = "交账状态展示")
    private String statusEnumText;
}
