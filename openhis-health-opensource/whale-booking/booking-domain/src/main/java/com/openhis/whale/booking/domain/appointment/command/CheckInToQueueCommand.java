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
 * 渠道报到入队命令。
 */
@Getter
@Setter
public class CheckInToQueueCommand extends BatchCommand<CheckInToQueueCommand.CheckInToQueueItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 检查into队列项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CheckInToQueueItemCommand {
        /** 预约kind。 */
        private String appointmentKind;
        /** 机构主键。 */
        private Long organizationId;
        /** 去向诊室。 */
        private Long destinationLocationId;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 渠道来源（会话、设备等审计信息）。 */
        private CreateAppointmentCommand.AppointmentOriginCommand origin;
    }
}

