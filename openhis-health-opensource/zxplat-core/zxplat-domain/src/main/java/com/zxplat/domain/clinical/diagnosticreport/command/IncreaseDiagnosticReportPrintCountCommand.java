/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 累加总检报告打印次数命令（REP-03）。
 */
@Getter
@Setter
public class IncreaseDiagnosticReportPrintCountCommand {

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 操作人主键。 */
    private Long operatorId;

    /** 递增量，默认 1。 */
    private Integer increaseBy;
}
