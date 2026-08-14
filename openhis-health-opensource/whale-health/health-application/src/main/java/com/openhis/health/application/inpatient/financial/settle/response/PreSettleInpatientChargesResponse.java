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

    /**
     * 渠道实付/少补预览（预交金充足时为 0）。
     *
     * <p>自费：{@code max(0, 本批应收 − 库内预交金)}；医保：{@code max(0, 个人现金 − 库内预交金)}。</p>
     */
    @Schema(description = "渠道实付/少补（预交金充足时为 0）")
    private BigDecimal displayAmount;

    /** 预结算时点的库内预交金余额（登记个人现金户 balance，供前端展示冲抵）。 */
    @Schema(description = "库内预交金余额")
    private BigDecimal prepayNetBalance;

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

    /** 基本医保统筹（2303 hifp_pay，只读展示）。 */
    @Schema(description = "基本医保统筹基金支出")
    private BigDecimal ybHifpPay;

    /** 其他支出（2303 oth_pay，只读展示）。 */
    @Schema(description = "其他支出")
    private BigDecimal ybOthPay;

    /** 实际支付起付线（2303 act_pay_dedc，只读展示）。 */
    @Schema(description = "实际支付起付线")
    private BigDecimal ybActPayDedc;

    /** 公务员医疗补助（2303 cvlserv_pay，只读展示）。 */
    @Schema(description = "公务员医疗补助")
    private BigDecimal ybCvlservPay;

    /** 公务员标志（2303 cvlserv_flag，只读展示）。 */
    @Schema(description = "公务员标志")
    private String ybCvlservFlag;

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
