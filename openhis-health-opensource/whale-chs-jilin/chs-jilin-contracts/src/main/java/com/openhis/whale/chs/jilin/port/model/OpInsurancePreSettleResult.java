/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 门诊医保预结算结果。 */
@Data
@Accessors(chain = true)
public class OpInsurancePreSettleResult {

    private Long preSettleId;
    private String chrgBchno;
    private String mdtrtId;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
    private BigDecimal psnCashPay;
}
