/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 发起转科命令：记录源科室与目标科室及发起时间。
 */
@Getter
@Setter
public class InitiateDepartmentTransferCommand extends BatchCommand<InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条发起项。 */
    @Getter
    @Setter
    public static class InitiateDepartmentTransferItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 原科室主键。 */
        private Long fromDepartmentId;
        /** 目标科室主键。 */
        private Long toDepartmentId;
        /** 发起时间。 */
        private LocalDateTime initiatedAt;
    }
}
