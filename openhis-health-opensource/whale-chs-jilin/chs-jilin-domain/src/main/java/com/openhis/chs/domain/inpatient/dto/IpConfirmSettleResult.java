/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2304 正式结算结果。 */
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
