/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.result;

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

    /** 个人账户支出 */
    private BigDecimal acctPay;

    /** 个人现金支出 */
    private BigDecimal psnCashPay;

    /**
     * 实际支付起付线（2206 {@code act_pay_dedc}）。
     *
     * <p>仅展示透传，不参与本地应收重算；不落 {@code yb_clinic_pre_settle}。</p>
     */
    private BigDecimal actPayDedc;

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
