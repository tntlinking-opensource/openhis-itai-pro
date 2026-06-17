/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 住院医保预结算结果。 */
@Data
@Accessors(chain = true)
public class IpInsurancePreSettleResult {

    private Long preSettleId;
    private Long contractId;
    private String mdtrtId;
    private String setlId;
    private String settleMode;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
    private BigDecimal psnCashPay;
}
