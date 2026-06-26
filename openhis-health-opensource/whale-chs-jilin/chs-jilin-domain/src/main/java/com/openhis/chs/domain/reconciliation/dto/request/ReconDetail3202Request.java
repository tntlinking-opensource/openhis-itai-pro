/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class ReconDetail3202Request {

    private String contractBusNo;
    private Long contractId;
    private String setlOptins;
    /** 9101 上传后获得，编排时由服务端填充 */
    private String fileQuryNo;
    private String stmtBegndate;
    private String stmtEnddate;
    private String clrType;
    /** 退费结算标志，默认 0 */
    private String refdSetlFlag;
    private BigDecimal medfeeSumamt;
    private BigDecimal fundPaySumamt;
    private BigDecimal cashPayamt;
    private Integer fixmedinsSetlCnt;
}
