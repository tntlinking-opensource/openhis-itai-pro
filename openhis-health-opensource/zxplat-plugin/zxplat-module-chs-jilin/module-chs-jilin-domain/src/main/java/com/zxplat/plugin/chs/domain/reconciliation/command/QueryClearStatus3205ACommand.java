/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3205A】清算状态查询 */
@Data
@Accessors(chain = true)
public class QueryClearStatus3205ACommand {

    private String contractBusNo;
    private Long contractId;
    private String clrOptins;
    /** 10 已申/ 20 已受理/ 50 已清单 */
    private String clrStas;
    /** yyyyMM，查20/50 时必 */
    private String clrYm;
}
