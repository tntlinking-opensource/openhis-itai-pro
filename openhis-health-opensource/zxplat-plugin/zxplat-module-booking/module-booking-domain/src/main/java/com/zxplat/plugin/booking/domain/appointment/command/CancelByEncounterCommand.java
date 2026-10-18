/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 按就诊取消命令。
 */
@Getter
@Setter
public class CancelByEncounterCommand extends BatchCommand<CancelByEncounterCommand.CancelByEncounterItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 取消by就诊项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CancelByEncounterItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 原因。 */
        private String reason;
        /** cancellation原因编码。 */
        private String cancellationReasonCode;
    }
}
