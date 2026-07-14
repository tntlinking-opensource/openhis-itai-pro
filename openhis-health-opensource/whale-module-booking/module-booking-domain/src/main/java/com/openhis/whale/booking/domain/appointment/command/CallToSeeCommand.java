/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 叫号看诊命令。
 */
@Getter
@Setter
public class CallToSeeCommand extends BatchCommand<CallToSeeCommand.CallToSeeItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 呼叫to看诊项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CallToSeeItemCommand {
        /** 预约主键。 */
        private Long appointmentId;
        /** 执业者主键。 */
        private Long practitionerId;
    }
}
