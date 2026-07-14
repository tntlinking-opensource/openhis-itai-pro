/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 过号处理命令。
 */
@Getter
@Setter
public class NoShowCommand extends BatchCommand<NoShowCommand.NoShowItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 号爽约项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class NoShowItemCommand {
        /** 预约主键。 */
        private Long appointmentId;
        /** 原因编码。 */
        private String reasonCode;
    }
}
