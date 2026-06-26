/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class LocalClearApplyDto {

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
