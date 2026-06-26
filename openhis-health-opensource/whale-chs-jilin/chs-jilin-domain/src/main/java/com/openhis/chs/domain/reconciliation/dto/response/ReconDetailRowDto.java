/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 3202 输入文件行（本地结算快照）。 */
@Data
@Accessors(chain = true)
public class ReconDetailRowDto {

    private String setlId;
    private String mdtrtId;
    private String psnNo;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal acctPay;
    private String refdSetlFlag;
    /** OP / IP */
    private String source;
}
