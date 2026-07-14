/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院预结算响应（IF-IP-STL-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院预结算响应")
public class PreSettleInpatientChargesResponse {

    /** 住院临床结算支付草稿 ID（业务字段）。 */
    @Schema(description = "住院临床结算支付草稿 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;

    /** 应收合计。 */
    @Schema(description = "应收合计")
    private BigDecimal receivableTotal;

    /** 预览文案。 */
    @Schema(description = "预览文案")
    private String previewText;

    /** 是否已完成医保预结算。 */
    @Schema(description = "是否已完成医保预结算")
    private Boolean insurancePreSettled;

    /** 患者实付金额。 */
    @Schema(description = "患者实付金额（医保 psn_cash_pay）")
    private BigDecimal displayAmount;

    /** 医保医疗费总额。 */
    @Schema(description = "医保医疗费总额")
    private BigDecimal ybMedfeeSumamt;

    /** 医保基金支付。 */
    @Schema(description = "医保基金支付")
    private BigDecimal ybFundPaySumamt;

    /** 医保个人账户支付。 */
    @Schema(description = "医保个人账户支付")
    private BigDecimal ybAcctPay;

    /** 医保个人现金支付。 */
    @Schema(description = "医保个人现金支付")
    private BigDecimal ybPsnCashPay;

    /** 医保预结算快照 ID。 */
    @Schema(description = "医保预结算快照 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ybPreSettleId;

    /** 结算构成：INSURANCE_ONLY | SELF_PAY_ONLY。 */
    @Schema(description = "结算构成：INSURANCE_ONLY | SELF_PAY_ONLY")
    private String settlementComposition;

    /** 结算模式：DISCHARGE_FULL | MID_SETTLE。 */
    @Schema(description = "结算模式：DISCHARGE_FULL | MID_SETTLE")
    private String settleMode;
}
