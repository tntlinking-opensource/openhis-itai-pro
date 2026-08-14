/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 住院预结算结果（2303 setlinfo 摘要）。 */
@Data
@Accessors(chain = true)
public class InpatientChargePreSettleExtensionOut {

    /** 预结算快照 id；批次键为 {@code IP{preSettleId}}。 */
    private Long preSettleId;
    /** 费用性质 id。 */
    private Long contractId;
    /** 平台就诊 ID mdtrt_id。 */
    private String encounterPlatformId;
    /** 正式结算后的 setl_id（预结算阶段通常为空）。 */
    private String settlementId;
    /** 结算模式 DISCHARGE_FULL / MID_SETTLE。 */
    private String settleMode;
    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出。 */
    private BigDecimal acctPay;
    /** 个人现金支出。 */
    private BigDecimal psnCashPay;
    /**
     * 实际支付起付线（2303 {@code act_pay_dedc}）。
     *
     * <p>只读展示；不参与本地应收重算。</p>
     */
    private BigDecimal actPayDedc;
    /**
     * 公务员标志（2303 {@code cvlserv_flag}，0=否 / 1=是）。
     *
     * <p>只读展示。</p>
     */
    private String cvlservFlag;
    /**
     * 公务员医疗补助资金支出（2303 {@code cvlserv_pay}）。
     *
     * <p>只读展示；不参与本地应收重算。</p>
     */
    private BigDecimal cvlservPay;
    /**
     * 基本医保统筹基金支出（2303 {@code hifp_pay}）。
     *
     * <p>只读展示；与 {@link #fundPaySumamt} 区分。</p>
     */
    private BigDecimal hifpPay;
    /**
     * 其他支出（2303 {@code oth_pay}，含电子钱包等）。
     *
     * <p>只读展示；不参与本地应收重算。</p>
     */
    private BigDecimal othPay;
}
