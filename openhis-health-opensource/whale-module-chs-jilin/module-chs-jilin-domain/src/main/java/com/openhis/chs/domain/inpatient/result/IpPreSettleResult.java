/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2303 预结算结果 */
@Data
@Accessors(chain = true)
public class IpPreSettleResult {

    /** 预结算快照主键 */
    private Long preSettleId;

    /** 费用性质 id */
    private Long contractId;

    /** 平台就诊 ID mdtrt_id */
    private String mdtrtId;

    /** 正式结算后的 setl_id（预结算阶段通常为空） */
    private String setlId;

    /** 结算模式（出院全结中途结算等） */
    private String settleMode;

    /** 中途结算标志 */
    private String midSetlFlag;

    /** 医疗费总额 */
    private BigDecimal medfeeSumamt;

    /** 基金支付总额 */
    private BigDecimal fundPaySumamt;

    /** 个人账户支出 */
    private BigDecimal acctPay;

    /** 个人现金支出 */
    private BigDecimal psnCashPay;

    /** 实际支付起付线（2303 act_pay_dedc，只读展示） */
    private BigDecimal actPayDedc;

    /** 公务员标志（2303 cvlserv_flag，只读展示） */
    private String cvlservFlag;

    /** 公务员医疗补助（2303 cvlserv_pay，只读展示） */
    private BigDecimal cvlservPay;

    /** 基本医保统筹基金支出（2303 hifp_pay，只读展示） */
    private BigDecimal hifpPay;

    /** 其他支出（2303 oth_pay，只读展示） */
    private BigDecimal othPay;
}
