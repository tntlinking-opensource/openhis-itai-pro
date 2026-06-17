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

/**
 * 候诊队列分页列表行模型。
 */
@Getter
@Setter
public class AppointmentQueuePageModel {
    /** 主键。 */
    private Long id;
    /** 就诊主键。 */
    private Long encounterId;
    /** 分诊等级。 */
    private String triageLevel;

    /** 队列排序。 */
    private Integer displayOrder;

    /** 展示状态（ACTIVE）。 */
    private String statusView;

    /** 业务单号。 */
    private String busNo;

    /** 患者主键。 */
    private Long patientId;

    /** 预约分类。 */
    private String appointmentKind;

    /** 计划开始时刻。 */
    private java.time.LocalDateTime startInstant;

    /** 最近叫号时间。 */
    private java.time.LocalDateTime calledAt;

    /** 关联号源主键。 */
    private Long slotId;
}

