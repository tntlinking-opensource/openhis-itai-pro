/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 月历顶栏摘要（2.23）。
 */
@Getter
@Setter
public class ScheduleMonthSummaryDto {

    /** 自然月内「有已发布日程」的天数。 */
    private Integer publishedDayCount;

    /** 自然月内「有草稿日程」的天数。 */
    private Integer draftDayCount;

    /** 自然月内「无任何日程」的天数。 */
    private Integer unscheduledDayCount;

    /** 自然月内「有已停用日程」的天数。 */
    private Integer inactiveDayCount;

    /** 自然月内有效门诊预约总数。 */
    private Integer bookedAppointmentCount;

    /** 自然月内可见日程条目总数（含同日多条）。 */
    private Integer totalScheduleCount;
}
