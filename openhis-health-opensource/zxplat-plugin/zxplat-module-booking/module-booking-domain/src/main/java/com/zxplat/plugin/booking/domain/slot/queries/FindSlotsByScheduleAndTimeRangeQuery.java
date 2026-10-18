/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按日程与时间窗查询时段入参（DM-WKF-010）。
 */
@Getter
@Setter
public class FindSlotsByScheduleAndTimeRangeQuery {

    /** 日程标识。 */
    private Long scheduleId;

    /** 起（包含）。 */
    private LocalDateTime startTime;

    /** 止（包含）。 */
    private LocalDateTime endTime;

    /** 状态码过滤（可选）。 */
    private List<String> statusCodeList;
}
