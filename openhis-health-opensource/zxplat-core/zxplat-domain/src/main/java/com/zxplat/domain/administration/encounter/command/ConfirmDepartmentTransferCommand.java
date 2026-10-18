/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 确认转科命令：确认患者转入目标科室的生效时间。
 */
@Getter
@Setter
public class ConfirmDepartmentTransferCommand extends BatchCommand<ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条确认项。 */
    @Getter
    @Setter
    public static class ConfirmDepartmentTransferItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 目标科室主键。 */
        private Long targetDepartmentId;
        /** 转科生效时间。 */
        private LocalDateTime effectiveAt;
    }
}
