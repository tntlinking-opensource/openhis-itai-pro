/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 医保预结算分组预览（混合结算展示）。 */
@Data
@Accessors(chain = true)
@Schema(description = "医保预结算分组")
public class OpInsurancePreSettleGroupDto {

    /** 组序号（从 1 起）。 */
    @Schema(description = "组序号")
    private Integer groupIndex;
    /** 医疗类别编码。 */
    @Schema(description = "医疗类别编码")
    private String medType;
    /** 医疗类别展示名。 */
    @Schema(description = "医疗类别展示名")
    private String medTypeDisplay;
    /** 慢病诊断医保码。 */
    @Schema(description = "慢病诊断医保码")
    private String diseCodg;
    /** 慢病诊断名称。 */
    @Schema(description = "慢病诊断名称")
    private String diseName;
    /** 医保合同主键。 */
    @Schema(description = "合同主键")
    private Long contractId;
    /** 记账账户主键。 */
    @Schema(description = "记账账户主键")
    private Long accountId;
    /** 本组收费项 id 列表。 */
    @Schema(description = "本组收费项")
    private List<Long> chargeItemIds;
    /** 医保收费批次号。 */
    @Schema(description = "医保收费批次号")
    private String chrgBchno;
    /** 平台就诊 id（mdtrt_id）。 */
    @Schema(description = "平台就诊 ID")
    private String mdtrtId;
    /** 预结算快照主键。 */
    @Schema(description = "预结算快照主键")
    private Long preSettleId;
    /** 医疗费总额。 */
    @Schema(description = "医疗费总额")
    private BigDecimal medfeeSumamt;
    /** 基金支付总额（fund_pay_sumamt；含统筹及其他基金，不等于单列统筹）。 */
    @Schema(description = "基金支付总额")
    private BigDecimal fundPaySumamt;
    /** 个人账户支付金额（含共济；规范上已含 {@link #acctMulaidPay}）。 */
    @Schema(description = "个账支付")
    private BigDecimal acctPay;
    /**
     * 家庭共济支付金额（2206 {@code acct_mulaid_pay}）。
     *
     * <p>仅收银展示；是 {@link #acctPay} 中的共济分项。</p>
     */
    @Schema(description = "家庭共济支付")
    private BigDecimal acctMulaidPay;
    /** 个人现金支付金额（混合结算患者应付）。 */
    @Schema(description = "个人现金")
    private BigDecimal psnCashPay;

    /**
     * 本组起付线（act_pay_dedc）。
     * <p>一笔预结算只含一个病种，此值即该笔平台报价，与随后 2207 及小票一致。</p>
     */
    @Schema(description = "本组起付线（单病种一笔的平台报价）")
    private BigDecimal actPayDedc;

    /** 本组统筹基金支付（hifp_pay，不含其他基金）。 */
    @Schema(description = "本组统筹基金支付")
    private BigDecimal hifpPay;
}
