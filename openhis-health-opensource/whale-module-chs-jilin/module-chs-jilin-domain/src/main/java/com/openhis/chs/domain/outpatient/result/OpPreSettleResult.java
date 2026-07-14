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

    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private String mdtrtCertType;
    private String acctUsedFlag;
}
