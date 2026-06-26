/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class ApplyJilinClear3203ARequest {

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
