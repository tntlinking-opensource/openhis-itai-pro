/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2206 预结算结果 */
@Data
@Accessors(chain = true)
public class OpPreSettleResult {

    /** 预结算快照主键 */
    private Long preSettleId;

    /** 收费批次chrg_bchno */
    private String chrgBchno;

    /** 平台就诊 ID mdtrt_id */
    private String mdtrtId;

    /** 医疗费总额 */
    private BigDecimal medfeeSumamt;

    /** 基金支付总额 */
    private BigDecimal fundPaySumamt;

    /** 个人账户支出（含共济；规范上已含 {@link #acctMulaidPay}） */
    private BigDecimal acctPay;

    /**
     * 个人账户共济支付金额（2206 {@code acct_mulaid_pay}）。
     *
     * <p>仅展示透传，不落 {@code yb_clinic_pre_settle}；是 {@link #acctPay} 中的共济分项。</p>
     */
    private BigDecimal acctMulaidPay;

    /** 个人现金支出 */
    private BigDecimal psnCashPay;

    /**
     * 实际支付起付线（2206 {@code act_pay_dedc}）。
     *
     * <p>不落 {@code yb_clinic_pre_settle}。同一平台就诊多组预结算时，
     * {@code SharedMdtrtDeductiblePreviewAdjuster} 会按正式结算顺序改写后续组的本字段，
     * 并同步改写基金与个人现金预览；2207 仍读库内原始 2206 快照。</p>
     */
    private BigDecimal actPayDedc;

    /**
     * 基本医疗保险统筹基金支付比例（2206 {@code pool_prop_selfpay}）。
     *
     * <p>仅供同一次就诊多组起付线预览收口，不落预结算表，不参与 2207 报文。</p>
     */
    private BigDecimal poolPropSelfpay;

    /**
     * 公务员标志（2206 {@code cvlserv_flag}，字典 0/1）。
     *
     * <p>仅展示透传，不落预结算快照表。</p>
     */
    private String cvlservFlag;

    /**
     * 公务员医疗补助资金支出（2206 {@code cvlserv_pay}）。
     *
     * <p>仅展示透传，不参与本地应收重算。</p>
     */
    private BigDecimal cvlservPay;

    /**
     * 基本医保统筹基金支出（2206 {@code hifp_pay}）。
     *
     * <p>仅展示透传；与 {@link #fundPaySumamt}（基金总额）区分，勿互相顶替。</p>
     */
    private BigDecimal hifpPay;

    /**
     * 其他支出（2206 {@code oth_pay}，含电子钱包等）。
     *
     * <p>仅展示透传，不参与本地应收重算。</p>
     */
    private BigDecimal othPay;

    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private String mdtrtCertType;
    private String acctUsedFlag;
}
