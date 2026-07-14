/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.reconcile.result;

import lombok.Data;

import java.math.BigDecimal;

/** 博思 checkTotalData* 核对统计（平台侧）。 */
@Data
public class BossReconcileTotalResult {

    private boolean success;
    private String resultCode;
    private String rawMessage;
    private long totalCount;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private long pageCount;
}
