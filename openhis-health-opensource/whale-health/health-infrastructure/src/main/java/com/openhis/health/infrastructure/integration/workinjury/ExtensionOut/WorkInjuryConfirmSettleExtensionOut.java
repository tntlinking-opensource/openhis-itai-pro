/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.workinjury.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 工伤本地落账确认结果。 */
@Data
@Accessors(chain = true)
public class WorkInjuryConfirmSettleExtensionOut {

    private Long settlementRecordId;
    private String setlId;
    private String chrgBchno;
    private String mdtrtId;
    private Long accountId;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
    private BigDecimal psnCashPay;
}
