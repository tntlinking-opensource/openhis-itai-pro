/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 开始执行命令。
 */
@Getter
@Setter
/**
 * StartProcedureExecutionCommand。
 */
public class StartProcedureExecutionCommand {
    /** 操作主键。 */
    /**
     * procedureId。
     */
    private Long procedureId;
    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
