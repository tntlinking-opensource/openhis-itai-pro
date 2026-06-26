/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 就诊平台挂号登记快照（列表 enrichment）。 */
@Data
@Accessors(chain = true)
public class OpInsurancePlatformRegistrationSnapshot {

    private Long encounterId;
    private Long contractId;
    private String mdtrtId;
    private String insuplcAdmdvs;
    private Boolean platformRegistered;
    private Boolean insurancePreSettled;
    private java.math.BigDecimal ybFundPaySumamt;
    private java.math.BigDecimal ybAcctPay;
    private java.math.BigDecimal ybPsnCashPay;
}
