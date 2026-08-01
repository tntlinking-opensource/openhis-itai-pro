/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 团检批量预结算响应。
 */
@Getter
@Setter
public class PreSettleBatchResponse {

    /** 预计结算人数。 */
    private int estimatedCount;

    /** 预计应付金额（占位）。 */
    private BigDecimal estimatedAmount = BigDecimal.ZERO;
}
