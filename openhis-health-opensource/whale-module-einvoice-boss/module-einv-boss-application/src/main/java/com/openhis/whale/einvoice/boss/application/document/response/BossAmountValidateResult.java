/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.document.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 金额校验结果。
 */
@Data
public class BossAmountValidateResult {

    private boolean valid;
    private BigDecimal lineSum;
    private BigDecimal totalAmt;
    private BigDecimal diff;
}
