/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class QueryClearOrg3206ARequest {

    private String contractBusNo;
    private Long contractId;
    private Integer pageNum;
    private Integer pageSize;
}
