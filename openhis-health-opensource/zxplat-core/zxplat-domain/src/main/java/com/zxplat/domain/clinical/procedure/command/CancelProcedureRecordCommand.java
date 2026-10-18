/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消执行命令。
 */
@Getter
@Setter
public class CancelProcedureRecordCommand {
    /** 操作主键。 */
    private Long procedureId;
    /** 状态原因文本。 */
    private String statusReasonText;
    /** 退费主键。 */
    private Long refundId;
    /** 操作人主键。 */
    private Long operatorId;
}
