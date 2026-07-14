/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.slot.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 可预约时段模型（DM-WKF-010）。
 */
@Getter
@Setter
public class BookableSlotModel {

    /** 时段标识。 */
    private Long slotId;

    /** 日程标识。 */
    private Long scheduleId;

    /** 开始时刻。 */
    private LocalDateTime startTime;

    /** 结束时刻。 */
    private LocalDateTime endTime;

    /** 是否满员。 */
    private Boolean isFull;
}
