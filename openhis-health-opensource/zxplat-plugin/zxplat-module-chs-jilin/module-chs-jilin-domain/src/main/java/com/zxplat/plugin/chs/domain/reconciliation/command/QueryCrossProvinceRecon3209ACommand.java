/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3209A】跨省三方对账失败数据查询 */
@Data
@Accessors(chain = true)
public class QueryCrossProvinceRecon3209ACommand {

    private String contractBusNo;
    private Long contractId;
    private String clrType;
    private String begndate;
    private String enddate;
    private String clrOptins;
}
