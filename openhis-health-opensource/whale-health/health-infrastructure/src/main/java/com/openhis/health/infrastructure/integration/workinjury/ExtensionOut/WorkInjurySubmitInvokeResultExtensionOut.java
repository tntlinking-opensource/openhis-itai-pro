/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.workinjury.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 工伤 Win invoke submit 结果。 */
@Data
@Accessors(chain = true)
public class WorkInjurySubmitInvokeResultExtensionOut {

    private String sessionId;
    private String nextAction;
    private String errMsg;
    private String summary;
    private Long settlementRecordId;
    private String setlId;
    private String chrgBchno;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal psnCashPay;
}
