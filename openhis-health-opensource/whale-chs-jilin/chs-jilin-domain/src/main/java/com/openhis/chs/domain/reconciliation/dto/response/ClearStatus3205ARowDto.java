/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class ClearStatus3205ARowDto {

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
