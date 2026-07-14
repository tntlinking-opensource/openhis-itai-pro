/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【3201】日对账请求（application {@code Recon3201Invoker} 出站） */
@Data
@Accessors(chain = true)
public class ReconDaily3201Command {

    private String contractBusNo;
    private Long contractId;
    private String insutype;
    private String clrType;
    private String setlOptins;
    private String stmtBegndate;
    private String stmtEnddate;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
    private Integer fixmedinsSetlCnt;
}
