/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地对账/清算流水查询条件 */
@Data
@Accessors(chain = true)
public class ListLocalReconRecordsCommand {

    private String contractBusNo;
    /** yyyyMM，可 */
    private String monthFilter;
}
