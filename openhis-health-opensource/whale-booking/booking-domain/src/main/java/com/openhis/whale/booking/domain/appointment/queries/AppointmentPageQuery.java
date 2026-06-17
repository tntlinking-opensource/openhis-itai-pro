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

package com.openhis.whale.booking.domain.appointment.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 门诊预约分页查询入参。
 */
@Getter
@Setter
public class AppointmentPageQuery {

    /** 机构/科室主键。 */
    private Long organizationId;

    /** 患者主键（可选）。 */
    private Long patientId;

    /** 计划开始日期起（含，按 startInstant 落点）。 */
    private LocalDate dateFrom;

    /** 计划开始日期止（含）。 */
    private LocalDate dateTo;

    /** 业务单号关键字（可选）。 */
    private String keyword;

    /** 页码（1-based）。 */
    private Integer pageNo;

    /** 页大小。 */
    private Integer pageSize;
}
