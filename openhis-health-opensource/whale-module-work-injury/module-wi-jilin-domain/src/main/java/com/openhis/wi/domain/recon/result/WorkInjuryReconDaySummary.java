/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.recon.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 工伤对账：单日 settlement ↔ payment 汇总。 */
@Data
@Accessors(chain = true)
public class WorkInjuryReconDaySummary {

    private LocalDate day;

    private BigDecimal medfeeSumamt;

    private BigDecimal fundPaySumamt;

    private BigDecimal psnCashPay;

    private BigDecimal paymentMedfee;

    private BigDecimal paymentHifp;

    private BigDecimal paymentCash;

    /** 结算回执笔数（含 REVOKED）。 */
    private Integer setlCount;

    /** CONFIRMED 且 settlement↔payment 金额全平的笔数。 */
    private Integer balancedCount;

    /** CONFIRMED 但缺收银或金额不平的笔数。 */
    private Integer unbalancedCount;

    /** pending / balanced / unbalanced（来自 yb_wi_recon_daily）。 */
    private String localStatus;

    /** 用户是否点过「确认账平」。 */
    private Boolean confirmed;

    private BigDecimal platformMedfee;
    private BigDecimal platformHifp;
    private BigDecimal platformCash;

    /** LOCAL / SNYD / QGYD */
    private String reconScope;
}
