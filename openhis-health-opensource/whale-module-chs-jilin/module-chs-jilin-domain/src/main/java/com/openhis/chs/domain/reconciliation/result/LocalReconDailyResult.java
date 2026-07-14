/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 本地日对账流水出参 */
@Data
@Accessors(chain = true)
public class LocalReconDailyResult {

    private Long id;
    private String contractBusNo;
    private String insutype;
    private String clrType;
    private String setlOptins;
    private String stmtBegndate;
    private String stmtEnddate;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
    private Integer fixmedinsSetlCnt;
    private String stmtRslt;
    private String stmtRsltDscr;
    private String createdAt;
}
