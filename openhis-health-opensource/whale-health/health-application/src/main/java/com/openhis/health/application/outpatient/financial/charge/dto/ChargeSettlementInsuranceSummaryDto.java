/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊收费页患者信息栏：已结算支付的医保分摊摘要（与收费小票结算单口径一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "已结算医保分摊摘要")
public class ChargeSettlementInsuranceSummaryDto {

    /** 关联支付流水主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "支付流水主键")
    private Long paymentId;

    /** 支付业务号（如 PMR-…）。 */
    @Schema(description = "支付业务号")
    private String paymentNo;

    /** 结算/账单时间。 */
    @Schema(description = "账单时间")
    private java.time.LocalDateTime billDate;

    /**
     * 收款人姓名（支付流水 {@code enterer_id} → 医务人员主档姓名；与收费小票口径一致）。
     */
    @Schema(description = "收款人姓名")
    private String cashierName;

    /** 支付主单展示金额（患者应付）。 */
    @Schema(description = "支付展示金额")
    private BigDecimal displayAmount;

    /** 是否含医保渠道（统筹/个账）。 */
    @Schema(description = "是否医保结算")
    private boolean insuranceSettled;

    /** 医疗费总额（支付明细合计；无明细时为空）。 */
    @Schema(description = "医疗费总额")
    private BigDecimal medfeeSumamt;

    /** 统筹基金支付（{@code INSURANCE_FUND}）。 */
    @Schema(description = "统筹基金支付")
    private BigDecimal fundPaySumamt;

    /** 个人账户支付（{@code INSURANCE_ACCT}）。 */
    @Schema(description = "个人账户支付")
    private BigDecimal acctPay;

    /** 个人现金/自付渠道合计（现金/微信/支付宝/银行卡等）。 */
    @Schema(description = "个人自付合计")
    private BigDecimal psnCashPay;

    /** 本支付单据关联的收费项主键（来自 payment.charge_item_ids）。 */
    @Schema(description = "本支付关联收费项主键列表")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> chargeItemIds;

    /** 本支付关联收费明细（只读对照；含收银台列表可能未返回的挂号费/已退费项）。 */
    @Schema(description = "本支付关联收费明细行")
    private List<ChargeSettlementPaymentLineDto> chargeLines;

    /**
     * 国家局 2206/2207 setlinfo 分摊细项（来自 {@code yb_settlement_record}）。
     *
     * <p>无正式结算回执时为空；有回执时优先用于医保分摊展示。</p>
     */
    @Schema(description = "医保结算分摊细项（2206/2207 setlinfo）")
    private ChargeSettlementSetlinfoDto setlinfo;
}
