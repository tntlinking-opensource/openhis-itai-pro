/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class ClearAmountSummaryDto {

    private BigDecimal medfeeSumamt;
    private BigDecimal medSumfee;
    private BigDecimal fundAppySum;
    private BigDecimal cashPayamt;
    private BigDecimal acctPay;
    private Integer setlCount;
}
