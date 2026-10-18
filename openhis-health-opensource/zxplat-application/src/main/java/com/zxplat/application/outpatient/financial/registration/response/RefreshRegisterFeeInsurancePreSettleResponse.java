/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 刷新挂号费医保预结算响应（2205→2206 重算后分摊）。 */
@Data
@Accessors(chain = true)
@Schema(description = "刷新挂号费医保预结算响应")
public class RefreshRegisterFeeInsurancePreSettleResponse {

    /** 支付草稿 ID。 */
    @Schema(description = "支付草稿ID")
    private Long paymentDraftId;

    /** 刷新后个人现金应付（与支付草稿 displayAmount 一致）。 */
    @Schema(description = "个人现金应付（刷新后）")
    private BigDecimal totalAmount;

    /** 是否已完成医保预结算。 */
    @Schema(description = "是否已完成医保预结算")
    private Boolean insurancePreSettled;

    /** 医保收费批次号 chrg_bchno。 */
    @Schema(description = "医保批次号")
    private String ybChrgBchno;

    /** 医保医疗费总额。 */
    @Schema(description = "医保医疗费总额")
    private BigDecimal ybMedfeeSumamt;

    /** 医保基金支付总额。 */
    @Schema(description = "医保基金支付")
    private BigDecimal ybFundPaySumamt;

    /** 医保个人账户支付。 */
    @Schema(description = "医保个人账户支付")
    private BigDecimal ybAcctPay;

    /** 医保个人现金支付。 */
    @Schema(description = "医保个人现金支付")
    private BigDecimal ybPsnCashPay;

    /**
     * 实际支付起付线（2206 {@code act_pay_dedc}）。
     *
     * <p>仅确认弹窗展示，不参与应收重算。</p>
     */
    @Schema(description = "实际支付起付线")
    private BigDecimal ybActPayDedc;

    /**
     * 公务员标志（2206 {@code cvlserv_flag}，0=否 / 1=是）。
     *
     * <p>仅确认弹窗展示。</p>
     */
    @Schema(description = "公务员标志")
    private String ybCvlservFlag;

    /**
     * 公务员医疗补助资金支出（2206 {@code cvlserv_pay}）。
     *
     * <p>仅确认弹窗展示，不参与应收重算。</p>
     */
    @Schema(description = "公务员医疗补助资金支出")
    private BigDecimal ybCvlservPay;
}
