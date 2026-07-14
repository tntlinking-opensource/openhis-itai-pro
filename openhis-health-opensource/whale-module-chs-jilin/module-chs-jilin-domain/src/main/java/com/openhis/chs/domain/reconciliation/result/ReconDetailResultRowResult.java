/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【3202】明细对账结果文件行（9102 下载解析） */
@Data
@Accessors(chain = true)
public class ReconDetailResultRowResult {

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
