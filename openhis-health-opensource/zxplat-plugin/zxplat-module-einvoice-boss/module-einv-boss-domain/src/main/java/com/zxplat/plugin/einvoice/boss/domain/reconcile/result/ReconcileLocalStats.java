/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.reconcile.result;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 本地日结核对统计（health + boss 单据）。
 */
@Data
public class ReconcileLocalStats {

    /** 已开具笔数。 */
    private long issuedCount;

    /** 已开具金额合计。 */
    private BigDecimal issuedTotalAmt = BigDecimal.ZERO;
}
