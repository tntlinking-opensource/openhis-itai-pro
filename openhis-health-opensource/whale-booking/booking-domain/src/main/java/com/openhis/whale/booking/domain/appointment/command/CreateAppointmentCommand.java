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

import java.time.LocalDateTime;

/**
 * 创建候诊/预约项命令。
 */
@Getter
@Setter
public class CreateAppointmentCommand extends BatchCommand<CreateAppointmentCommand.CreateAppointmentItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class CreateAppointmentItemCommand {
        /** 预约分类。 */
        private String appointmentKind;
        /** 机构主键。 */
        private Long organizationId;
        /** 去向诊室。 */
        private Long destinationLocationId;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 队列排序。 */
        private Integer displayOrder;
        /** 号源主键。 */
        private Long slotId;
        /** 计划开始。 */
        private LocalDateTime startInstant;
        /** 计划结束。 */
        private LocalDateTime endInstant;
        /** 预约类型编码（医技场景存 serviceRequestId 字符串）。 */
        private String appointmentTypeCode;
        /** 渠道来源。 */
        private AppointmentOriginCommand origin;
    }

    /**
     * 渠道来源子命令。
     */
    @Getter
    @Setter
    public static class AppointmentOriginCommand {
        private String originChannel;
        private String originChannelSessionId;
        private Long originSourceDeviceId;
        private Long originOperatorId;
        private String originClientAppVersion;
    }
}

