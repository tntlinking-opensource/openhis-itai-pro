/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 混合预结算中的自费费用汇总（不调医保平台） */
@Data
@Accessors(chain = true)
public class OpSelfPayRollup {

    private Long accountId;
    private List<Long> chargeItemIds;
    private BigDecimal totalAmount;
}
