/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.recon.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 工伤对账：单日结算明细行（供 1321 逐笔比对）。 */
@Data
@Accessors(chain = true)
public class WorkInjuryReconSettlementRow {

    private Long settlementRecordId;

    private Long encounterId;

    /** 门诊/住院流水号（1321 ipt_otp_no）。 */
    private String iptOtpNo;

    private String mdtrtId;

    private String setlId;

    private String psnName;

    private LocalDateTime setlTime;

    private BigDecimal medfeeSumamt;

    private BigDecimal fundPaySumamt;

    private BigDecimal psnCashPay;

    /** 2207 Win msgid；空表示本地未记录成功回执。 */
    private String msgid2207;

    /** 结算状态 CONFIRMED / REVOKED。 */
    private String status;

    /** fin_payment_reconciliation.id。 */
    private Long paymentId;

    private String paymentStatus;

    private BigDecimal paymentMedfee;

    private BigDecimal paymentHifp;

    private BigDecimal paymentCash;
}
