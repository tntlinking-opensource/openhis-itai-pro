/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 完成执行命令。
 */
@Getter
@Setter
public class CompleteProcedureRecordCommand {
    /** 操作主键。 */
    private Long procedureId;
    /** 结果文本。 */
    private String resultText;
    /** 报告JSON。 */
    private String reportJson;
    /** 操作人主键。 */
    private Long operatorId;
}
