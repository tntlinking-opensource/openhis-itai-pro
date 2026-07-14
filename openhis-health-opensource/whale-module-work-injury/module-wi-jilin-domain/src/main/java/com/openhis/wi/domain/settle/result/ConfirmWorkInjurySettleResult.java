/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.settle.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 2207 落账结果（供 {@code fin_payment_rec_detail} 写基金/个账拆分行）。
 */
@Data
@Accessors(chain = true)
public class ConfirmWorkInjurySettleResult {

    /** {@code yb_settlement_record.id}。 */
    private Long settlementRecordId;
    /** 平台 setl_id。 */
    private String setlId;
    /** 收费批次号。 */
    private String chrgBchno;
    /** 平台 mdtrt_id。 */
    private String mdtrtId;
    /** 个账账户 id（acct_pay &gt; 0 时）。 */
    private Long accountId;
    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出。 */
    private BigDecimal acctPay;
    /** 个人现金支出。 */
    private BigDecimal psnCashPay;
}
