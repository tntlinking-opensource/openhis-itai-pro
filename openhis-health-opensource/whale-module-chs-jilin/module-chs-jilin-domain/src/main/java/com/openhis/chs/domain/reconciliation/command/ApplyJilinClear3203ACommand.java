/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【3203A】吉林清算申请 */
@Data
@Accessors(chain = true)
public class ApplyJilinClear3203ACommand {

    private String contractBusNo;
    private Long contractId;
    private String clrType;
    private BigDecimal medfeeSumamt;
    private BigDecimal medSumfee;
    private BigDecimal fundAppySum;
    private BigDecimal cashPayamt;
    private BigDecimal acctPay;
    private String begndate;
    private String enddate;
    private String clrOptins;
}
