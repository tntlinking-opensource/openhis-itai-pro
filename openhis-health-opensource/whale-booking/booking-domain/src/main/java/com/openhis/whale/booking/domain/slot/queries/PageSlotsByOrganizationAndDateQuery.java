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

package com.openhis.whale.booking.domain.slot.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 按机构与日期分页查询时段入参（DM-WKF-010）。
 */
@Getter
@Setter
public class PageSlotsByOrganizationAndDateQuery {

    /** 机构标识。 */
    private Long organizationId;

    /** 日期（按 startTime 落日统计）。 */
    private LocalDate date;

    /** 页码（从 1 开始）。 */
    private Integer pageNo;

    /** 页大小。 */
    private Integer pageSize;

    /** 日程主键（可选）。 */
    private Long scheduleId;

    /** 状态码（可选）。 */
    private String statusCode;

    /** 日程主键集合（可选，用于医师等参与者收窄）。 */
    private List<Long> scheduleIds;
}

