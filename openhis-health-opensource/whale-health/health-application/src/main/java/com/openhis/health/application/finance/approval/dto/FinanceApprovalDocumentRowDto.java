/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.approval.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 财务审批统一列表行（IF-APR-02，一行一 bus_no）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "财务审批单据列表行")
public class FinanceApprovalDocumentRowDto {

    /** 文档种类：SUPPLY / PRICE_ADJUSTMENT。 */
    @Schema(description = "文档种类")
    private String documentKind;

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 供应码或 PRICE_ADJUSTMENT。 */
    @Schema(description = "单据类型 code")
    private String typeEnum;

    /** 单据类型展示名。 */
    @Schema(description = "单据类型展示名")
    private String typeEnumText;

    /** 供应或调价状态码。 */
    @Schema(description = "审批状态 code")
    private String statusEnum;

    /** 审批状态展示名。 */
    @Schema(description = "审批状态展示名")
    private String statusEnumText;

    /** 库房位置主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房位置主键")
    private Long locationId;

    /** 库房名称。 */
    @Schema(description = "库房名称")
    private String locationName;

    /** 供应商主键（可空）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商主键")
    private Long supplierId;

    /** 供应商名称（可空）。 */
    @Schema(description = "供应商名称")
    private String supplierName;

    /** 品类表名（可空）。 */
    @Schema(description = "品类表名")
    private String itemTable;

    /** 品类展示：药品/耗材。 */
    @Schema(description = "品类展示")
    private String itemTableText;

    /** 制单人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "制单人主键")
    private Long applicantId;

    /** 制单人姓名。 */
    @Schema(description = "制单人姓名")
    private String applicantName;

    /** 审批人主键（可空）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 审批人姓名。 */
    @Schema(description = "审批人姓名")
    private String approverName;

    /** 制单时间展示串。 */
    @Schema(description = "制单时间")
    private String occurrenceTime;

    /** 申请时间展示串（供应）。 */
    @Schema(description = "申请时间")
    private String applyTime;

    /** 提交时间展示串（调价）。 */
    @Schema(description = "提交时间")
    private String submitTime;

    /** 审批时间展示串（供应）。 */
    @Schema(description = "审批时间（供应）")
    private String approvalTime;

    /** 审批时间展示串（调价）。 */
    @Schema(description = "审批时间（调价）")
    private String approveTime;

    /** 进货金额（方案 B / 进货类型）。 */
    @Schema(description = "进货金额")
    private BigDecimal inboundAmount;

    /** 退货金额。 */
    @Schema(description = "退货金额")
    private BigDecimal returnAmount;

    /** 实盘金额。 */
    @Schema(description = "实盘金额")
    private BigDecimal stocktakeActualAmount;

    /** 盈亏金额（盘点/损益）。 */
    @Schema(description = "盈亏金额")
    private BigDecimal profitLossAmount;

    /**
     * 调价进价盈亏（单号维度 {@code totalPurchaseImpact}；与售价盈亏分列，禁止加总）。
     */
    @Schema(description = "调价进价盈亏")
    private BigDecimal priceAdjustmentPurchaseImpact;

    /**
     * 调价售价盈亏（单号维度 {@code totalSellingImpact}；与进价盈亏分列，禁止加总）。
     */
    @Schema(description = "调价售价盈亏")
    private BigDecimal priceAdjustmentSellingImpact;

    /** 驳回原因（可空）。 */
    @Schema(description = "驳回原因")
    private String rejectReason;
}
