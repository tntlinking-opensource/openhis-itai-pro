/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 月历单日格子（含状态计数，2.23）。
 */
@Getter
@Setter
public class ScheduleCalendarMonthDayDto {

    /** 日历日。 */
    private LocalDate date;

    /** 是否属于当前查询自然月。 */
    private Boolean inMonth;

    /** 当日已发布日程数。 */
    private Integer activeCount;

    /** 当日草稿日程数。 */
    private Integer draftCount;

    /** 当日已停用日程数。 */
    private Integer inactiveCount;

    /** 当日可见日程摘要列表。 */
    private List<ScheduleCalendarCellDto> schedules;
}
