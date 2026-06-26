/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class LocalReconDailyDto {

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
