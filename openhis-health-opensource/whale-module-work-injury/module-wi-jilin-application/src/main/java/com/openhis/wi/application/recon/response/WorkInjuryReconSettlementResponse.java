/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.recon.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class WorkInjuryReconSettlementResponse {

    private Long settlementRecordId;
    private Long encounterId;
    private String iptOtpNo;
    private String mdtrtId;
    private String setlId;
    private String psnName;
    private LocalDateTime setlTime;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal psnCashPay;
    private String msgid2207;
    private String status;
    /** 本地是否已有 2207 回执标记。 */
    private Boolean posted2207;

    private Long paymentId;
    private String paymentStatus;
    private BigDecimal paymentMedfee;
    private BigDecimal paymentHifp;
    private BigDecimal paymentCash;
}
