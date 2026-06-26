/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class ReconDaily3201Request {

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
