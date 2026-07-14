/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 维护执行人命令。
 */
@Getter
@Setter
public class AppendProcedurePerformerCommand extends BatchCommand<AppendProcedurePerformerCommand.ProcedurePerformerItemCommand> {
    /** 操作主键。 */
    private Long procedureId;
    /** 操作人主键。 */
    private Long operatorId;

/**
 * 操作执行人项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ProcedurePerformerItemCommand {
        /** 主键。 */
        private Long id;
        /** 执业者主键。 */
        private Long practitionerId;
        /** 职能编码。 */
        private String functionCode;
        /** 开始时间。 */
        private LocalDateTime startTime;
        /** 结束时间。 */
        private LocalDateTime endTime;
        /** 机构来源主键。 */
        private String orgFromId;
    }
}
