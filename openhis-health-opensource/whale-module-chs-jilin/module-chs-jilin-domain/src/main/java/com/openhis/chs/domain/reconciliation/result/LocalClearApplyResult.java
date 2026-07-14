/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 本地清算申请流水出参 */
@Data
@Accessors(chain = true)
public class LocalClearApplyResult {

    private Long id;
    private String contractBusNo;
    private String clrType;
    private String clrOptins;
    private String begndate;
    private String enddate;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundAppySum;
    private BigDecimal acctPay;
    private BigDecimal cashPayamt;
    private String clrAppyEvtId;
    private String clrStas;
    private String status;
    private String createdAt;
}
