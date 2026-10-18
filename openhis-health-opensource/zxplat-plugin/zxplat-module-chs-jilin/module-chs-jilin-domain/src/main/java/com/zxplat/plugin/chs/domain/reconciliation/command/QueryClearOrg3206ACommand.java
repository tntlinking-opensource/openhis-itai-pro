/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3206A】清算机构查询 */
@Data
@Accessors(chain = true)
public class QueryClearOrg3206ACommand {

    private String contractBusNo;
    private Long contractId;
    private Integer pageNum;
    private Integer pageSize;
}
