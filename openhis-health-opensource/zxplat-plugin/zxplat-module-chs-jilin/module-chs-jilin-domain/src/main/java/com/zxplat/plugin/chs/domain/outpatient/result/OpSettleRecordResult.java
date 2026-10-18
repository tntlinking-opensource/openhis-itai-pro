/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 门诊医保正式结算回执摘要（{@code yb_settlement_record} 只读视图） */
@Data
@Accessors(chain = true)
public class OpSettleRecordResult {

    private Long settlementRecordId;
    private Long paymentId;
    private Long preSettleId;
    private Long contractId;
    /** 记账账户 id（{@code yb_settlement_record.account_id}）。 */
    private Long accountId;
    private String chrgBchno;
    private String setlId;
    private String mdtrtId;
    private String msgid2207;
    private String status;

    private BigDecimal medfeeSumamt;
    private BigDecimal fulamtOwnpayAmt;
    private BigDecimal overlmtSelfpay;
    private BigDecimal preselfpayAmt;
    private BigDecimal inscpScpAmt;
    private BigDecimal actPayDedc;
    private BigDecimal hifpPay;
    private BigDecimal poolPropSelfpay;
    /** 公务员标志（2207 {@code cvlserv_flag}，字典 0/1；支付记录展示用）。 */
    private String cvlservFlag;
    private BigDecimal cvlservPay;
    private BigDecimal hifesPay;
    private BigDecimal hifmiPay;
    private BigDecimal hifobPay;
    private BigDecimal mafPay;
    private BigDecimal hifdmPay;
    private BigDecimal othPay;
    private BigDecimal fundPaySumamt;
    private BigDecimal psnPartAmt;
    private BigDecimal acctPay;
    private BigDecimal psnCashPay;
    private BigDecimal hospPartAmt;
    private BigDecimal balc;
    private BigDecimal acctMulaidPay;
    private String clrOptins;
    private String clrWay;
    private String clrType;
}
