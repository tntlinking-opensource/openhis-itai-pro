/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
