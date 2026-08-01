/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.reportgrantext.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 标记报告已打印命令（DM-PEIS-005 REP-06）。
 */
@Getter
@Setter
public class MarkGrantPrintedCommand {

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 操作人标识。 */
    private Long operatorId;
}
