/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
