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

import java.time.LocalDate;
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
        /** 冗余：医疗服务主键（门诊防重）。 */
        private Long healthcareServiceId;
        /** 冗余：预约自然日（Asia/Shanghai）。 */
        private LocalDate bookingDate;
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
        /** 渠道编码（如 KIOSK / MINIAPP / COUNTER / DOCTOR_STATION / NURSE_STATION / OTHER）。 */
        private String originChannel;
        /** 渠道会话标识。 */
        private String originChannelSessionId;
        /** 来源设备标识（自助机 Device 等）。 */
        private Long originSourceDeviceId;
        /** 代操作操作者标识（护士站等）。 */
        private Long originOperatorId;
        /** 客户端版本（小程序/App 版本等）。 */
        private String originClientAppVersion;
    }
}

