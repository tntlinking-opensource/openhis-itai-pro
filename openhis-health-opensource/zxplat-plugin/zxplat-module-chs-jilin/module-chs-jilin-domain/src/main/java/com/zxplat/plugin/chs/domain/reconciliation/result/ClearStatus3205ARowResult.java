/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【3205A】清算状态查询行 */
@Data
@Accessors(chain = true)
public class ClearStatus3205ARowResult {

    private String fixmedinsName;
    private String fixmedinsCode;
    private String clrStas;
    private String optTime;
    private BigDecimal medfeeSumamt;
    private BigDecimal hiAgreSumfee;
    private BigDecimal fundAppySum;
    private BigDecimal cashPayamt;
    private BigDecimal acctPay;
    private String begndate;
    private String enddate;
    private String clrYm;
    private String clrOptins;
    private String opterId;
    private String opterName;
    private String optinsNo;
    private String clrAppyEvtId;
}
