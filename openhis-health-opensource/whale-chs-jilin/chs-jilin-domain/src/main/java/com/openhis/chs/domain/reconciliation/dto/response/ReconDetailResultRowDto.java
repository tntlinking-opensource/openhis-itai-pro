/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Data
@Accessors(chain = true)
public class ReconDetailResultRowDto {

    private String psnNo;
    private String mdtrtId;
    private String setlId;
    private String msgid;
    private String stmtRslt;
    private String refdSetlFlag;
    private String memo;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
}
