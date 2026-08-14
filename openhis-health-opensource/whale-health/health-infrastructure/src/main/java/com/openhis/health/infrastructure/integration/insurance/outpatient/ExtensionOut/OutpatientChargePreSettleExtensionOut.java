/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 门诊预结算结果（2206 setlinfo 摘要，health 中性字段名）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargePreSettleExtensionOut {

    /** 预结算快照 id。 */
    private Long preSettleId;
    /** 收费批次号 chrg_bchno。 */
    private String chargeBatchNo;
    /** 平台就诊 ID mdtrt_id。 */
    private String encounterPlatformId;
    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出。 */
    private BigDecimal acctPay;
    /** 个人现金支出（→ 收银 displayAmount）。 */
    private BigDecimal psnCashPay;
    /**
     * 实际支付起付线（2206 {@code act_pay_dedc}）。
     *
     * <p>只读展示；不参与本地应收/支付草稿金额重算。</p>
     */
    private BigDecimal actPayDedc;
    /**
     * 公务员标志（2206 {@code cvlserv_flag}，0=否 / 1=是）。
     *
     * <p>只读展示。</p>
     */
    private String cvlservFlag;
    /**
     * 公务员医疗补助资金支出（2206 {@code cvlserv_pay}）。
     *
     * <p>只读展示；不参与本地应收重算。</p>
     */
    private BigDecimal cvlservPay;
    /**
     * 基本医保统筹基金支出（2206 {@code hifp_pay}）。
     *
     * <p>只读展示；与 {@link #fundPaySumamt} 区分。</p>
     */
    private BigDecimal hifpPay;
    /**
     * 其他支出（2206 {@code oth_pay}，含电子钱包等）。
     *
     * <p>只读展示；不参与本地应收重算。</p>
     */
    private BigDecimal othPay;
}
