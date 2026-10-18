/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.queries;

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
