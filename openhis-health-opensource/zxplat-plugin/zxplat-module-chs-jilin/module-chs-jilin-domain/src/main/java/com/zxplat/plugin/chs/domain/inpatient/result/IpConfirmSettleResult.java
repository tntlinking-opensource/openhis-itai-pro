/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2304 正式结算结果 */
@Data
@Accessors(chain = true)
public class IpConfirmSettleResult {

    private Long settlementRecordId;
    private Long preSettleId;
    private String setlId;
    private String chrgBchno;
    private String mdtrtId;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
    private BigDecimal psnCashPay;
}
