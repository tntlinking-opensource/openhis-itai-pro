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

package com.openhis.whale.booking.domain.appointment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊预约列表读模型。
 */
@Getter
@Setter
public class AppointmentBookingListModel {

    /** 预约主键。 */
    private Long id;

    /** 业务单号。 */
    private String busNo;

    /** 预约分类。 */
    private String appointmentKind;

    /** 预约类型编码（医技场景存 serviceRequestId）。 */
    private String appointmentTypeCode;

    /** 患者主键。 */
    private Long patientId;

    /** 机构主键。 */
    private Long organizationId;

    /** 号源主键。 */
    private Long slotId;

    /** 日程主键（经 Slot 冗余解析，列表可为空）。 */
    private Long scheduleId;

    /** 计划开始。 */
    private LocalDateTime startInstant;

    /** 计划结束。 */
    private LocalDateTime endInstant;

    /** 去向诊室。 */
    private Long destinationLocationId;

    /** 展示状态（ACTIVE）。 */
    private String statusView;
}
