/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【3202】明细对账请求（9101 上传 + 3202 申请编排） */
@Data
@Accessors(chain = true)
public class ReconDetail3202Command {

    private String contractBusNo;
    private Long contractId;
    private String setlOptins;
    /** 9101 上传后获得，编排时由服务端填 */
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
