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

package com.openhis.whale.booking.domain.schedule.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 周历/月历日程查询入参。
 */
@Getter
@Setter
public class CalendarScheduleQuery {

    /** 机构/科室主键。 */
    private Long organizationId;

    /** 可见区间起（含）。 */
    private LocalDate rangeStart;

    /** 可见区间止（含）。 */
    private LocalDate rangeEnd;

    /** 医师参与者主键（可选）。 */
    private Long practitionerId;

    /** 诊室参与者主键（可选）。 */
    private Long locationId;

    /** 号源类型参与者主键（可选）。 */
    private Long healthcareServiceId;
}
