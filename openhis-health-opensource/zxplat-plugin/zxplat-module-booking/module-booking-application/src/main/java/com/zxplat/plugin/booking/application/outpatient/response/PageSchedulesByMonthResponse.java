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
 * 月历查询响应（2.23）。
 */
@Getter
@Setter
public class PageSchedulesByMonthResponse {

    /** 查询自然月 yyyy-MM。 */
    private String yearMonth;

    /** 自然月起（含）。 */
    private LocalDate monthStart;

    /** 自然月止（含）。 */
    private LocalDate monthEnd;

    /** 月历网格起（含，补齐至整周）。 */
    private LocalDate gridStart;

    /** 月历网格止（含，补齐至整周）。 */
    private LocalDate gridEnd;

    /** 月历格子（含跨月 padding 日）。 */
    private List<ScheduleCalendarMonthDayDto> days;

    /** 顶栏摘要。 */
    private ScheduleMonthSummaryDto summary;
}
