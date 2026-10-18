/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.result;

import com.zxplat.plugin.chs.domain.outpatient.command.OpSelfPayRollup;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 门诊医保混合预结算结果（多组 2204+2206 汇总） */
@Data
@Accessors(chain = true)
public class OpPreSettleBatchResult {

    private List<OpPreSettleGroupResult> groups;
    private OpSelfPayRollup selfPayRollup;
    private BigDecimal totalPsnCashPay;
}
