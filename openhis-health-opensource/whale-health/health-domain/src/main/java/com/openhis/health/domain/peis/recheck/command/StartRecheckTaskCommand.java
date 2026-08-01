/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.recheck.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 启动复查任务命令（确认复查项目后 in-progress）。
 */
@Getter
@Setter
public class StartRecheckTaskCommand {

    /** 复查任务主键。 */
    private Long taskId;

    /** 操作人标识。 */
    private Long operatorId;
}
