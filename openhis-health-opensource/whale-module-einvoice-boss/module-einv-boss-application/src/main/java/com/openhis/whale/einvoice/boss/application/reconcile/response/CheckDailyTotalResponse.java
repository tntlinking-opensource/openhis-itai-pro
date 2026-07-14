/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.reconcile.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 财务日结核对结果。
 */
@Data
public class CheckDailyTotalResponse {

    private LocalDate checkDate;
    private String dateMode;
    private String issueKind;

    private long localIssuedCount;
    private BigDecimal localIssuedAmount = BigDecimal.ZERO;

    private long bossTotalCount;
    private BigDecimal bossTotalAmount = BigDecimal.ZERO;

    private boolean bossSuccess;
    private String bossResultCode;
    private String bossMessage;

    private boolean matched;
    private long countDiff;
}
