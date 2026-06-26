/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SummarizeClearAmountRequest {

    private String contractBusNo;
    private Long contractId;
    /** yyyy-MM-dd */
    private String begndate;
    /** yyyy-MM-dd */
    private String enddate;
}
