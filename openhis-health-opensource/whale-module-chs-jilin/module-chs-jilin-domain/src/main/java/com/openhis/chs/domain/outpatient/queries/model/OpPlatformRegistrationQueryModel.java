/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.queries.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 就诊平台挂号登记快照（列行enrichment） */
@Data
@Accessors(chain = true)
public class OpPlatformRegistrationQueryModel {

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
