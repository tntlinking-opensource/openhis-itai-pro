/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class QueryClearStatus3205ARequest {

    private String contractBusNo;
    private Long contractId;
    private String clrOptins;
    /** 10 已申请 / 20 已受理 / 50 已清算 */
    private String clrStas;
    /** yyyyMM，查询 20/50 时必填 */
    private String clrYm;
}
