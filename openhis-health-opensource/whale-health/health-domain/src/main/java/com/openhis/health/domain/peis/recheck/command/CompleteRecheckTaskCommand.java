/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.recheck.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 完成复查任务命令（AS-PEIS-REC-003）。
 */
@Getter
@Setter
public class CompleteRecheckTaskCommand {

    /** 复查任务主键。 */
    private Long taskId;

    /** 复查总检报告主键。 */
    private Long diagnosticReportId;

    /** 操作人标识。 */
    private Long operatorId;
}
