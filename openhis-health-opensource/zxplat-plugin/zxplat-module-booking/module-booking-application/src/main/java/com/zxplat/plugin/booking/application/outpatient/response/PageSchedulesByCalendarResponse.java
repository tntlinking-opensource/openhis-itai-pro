/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 周历/月历查询响应（2.2）。
 */
@Getter
@Setter
public class PageSchedulesByCalendarResponse {

    /** 按日分组的日历格子。 */
    private List<ScheduleCalendarDayDto> days;
}
