/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.response;

import com.zxplat.application.outpatient.financial.registration.dto.ChargeItemPreviewDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 挂号预结算响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号预结算响应")
public class PreRegisterSettlementResponse {

    /** 就诊ID（业务字段）。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 支付草稿 ID。 */
    @Schema(description = "支付草稿ID")
    private Long paymentDraftId;

    /** 预结算总额。 */
    @Schema(description = "总额")
    private BigDecimal totalAmount;

    /** 预结算明细。 */
    @Schema(description = "明细")
    private List<ChargeItemPreviewDto> chargeItems;

    /** 关联预约主键（预约转挂号时回传）。 */
    @Schema(description = "预约ID")
    private Long appointmentId;

    /** 关联号源主键（预约转挂号时回传）。 */
    @Schema(description = "号源ID")
    private Long slotId;

    /** 是否已完成医保预结算（2203→2206）。 */
    @Schema(description = "是否已完成医保预结算")
    private Boolean insurancePreSettled;

    /** 医保批次号 chrg_bchno（缴费 confirm 2207 时使用）。 */
    @Schema(description = "医保批次号")
    private String ybChrgBchno;

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

    /**
     * 实际支付起付线（2206 {@code act_pay_dedc}）。
     *
     * <p>仅确认弹窗展示，不参与挂号费应收重算。</p>
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
     * <p>仅确认弹窗展示，不参与挂号费应收重算。</p>
     */
    @Schema(description = "公务员医疗补助资金支出")
    private BigDecimal ybCvlservPay;
}
