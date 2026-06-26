/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class QueryCrossProvinceRecon3209ARequest {

    private String contractBusNo;
    private Long contractId;
    private String clrType;
    private String begndate;
    private String enddate;
    private String clrOptins;
}
