/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 清算金额汇总与 3202 明细行预览查询条件 */
@Data
@Accessors(chain = true)
public class SummarizeClearAmountCommand {

    private String contractBusNo;
    private Long contractId;
    /** yyyy-MM-dd */
    private String begndate;
    /** yyyy-MM-dd */
    private String enddate;
}
