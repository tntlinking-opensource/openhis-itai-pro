/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2207 正式结算结果。 */
@Data
@Accessors(chain = true)
public class OpConfirmSettleResult {

    private Long settlementRecordId;
    private Long accountId;
    private String setlId;
    private String chrgBchno;
    private String mdtrtId;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
    private BigDecimal psnCashPay;
}
