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
 * 取消门诊就诊命令：退号或撤销就诊登记。
 */
@Getter
@Setter
public class CancelOutpatientRegistrationCommand extends BatchCommand<CancelOutpatientRegistrationCommand.CancelOutpatientRegistrationItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条取消项。 */
    @Getter
    @Setter
    public static class CancelOutpatientRegistrationItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 取消原因。 */
        private String cancelReason;
        /** 取消时间。 */
        private LocalDateTime cancelAt;
    }
}
