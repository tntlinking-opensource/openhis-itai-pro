/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 就诊平台挂号登记快照（列表 enrichment / 待挂号撤销判定）。 */
@Data
@Accessors(chain = true)
public class OutpatientRegistrationPlatformSnapshot {

    private Long encounterId;
    private Long contractId;
    private String mdtrtId;
    private String insuplcAdmdvs;
    private Boolean platformRegistered;
    private Boolean insurancePreSettled;
    private BigDecimal ybFundPaySumamt;
    private BigDecimal ybAcctPay;
    private BigDecimal ybPsnCashPay;
}
