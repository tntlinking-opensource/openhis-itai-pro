/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.application.recon.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 工伤对账日汇总（settlement ↔ payment）。 */
@Data
@Accessors(chain = true)
public class WorkInjuryReconDaySummaryResponse {

    /** yyyy-MM-dd */
    private String day;

    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal psnCashPay;

    private BigDecimal paymentMedfee;
    private BigDecimal paymentHifp;
    private BigDecimal paymentCash;

    private Integer setlCount;
    private Integer balancedCount;
    private Integer unbalancedCount;

    /** pending / balanced / unbalanced */
    private String localStatus;

    private Boolean confirmed;

    private BigDecimal platformMedfee;
    private BigDecimal platformHifp;
    private BigDecimal platformCash;

    /** LOCAL / SNYD / QGYD */
    private String reconScope;
}
