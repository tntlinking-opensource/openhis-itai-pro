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
 * 取消住院登记命令：撤销入院事实并记录原因；可携带账户余额快照供退费校验。
 */
@Getter
@Setter
public class CancelInpatientAdmissionCommand extends BatchCommand<CancelInpatientAdmissionCommand.CancelInpatientAdmissionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条取消项。 */
    @Getter
    @Setter
    public static class CancelInpatientAdmissionItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 取消原因。 */
        private String cancelReason;
        /** 取消时间。 */
        private LocalDateTime cancelAt;
        /** 账户余额快照（单位与账务域约定，可为空）。 */
        private Long accountBalance;
    }
}
