/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 按预约主键取消命令。
 */
@Getter
@Setter
public class CancelAppointmentCommand extends BatchCommand<CancelAppointmentCommand.CancelAppointmentItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class CancelAppointmentItemCommand {
        /** 预约主键。 */
        private Long appointmentId;
        /** 取消原因文本。 */
        private String reason;
        /** 取消原因编码。 */
        private String cancellationReasonCode;
    }
}
