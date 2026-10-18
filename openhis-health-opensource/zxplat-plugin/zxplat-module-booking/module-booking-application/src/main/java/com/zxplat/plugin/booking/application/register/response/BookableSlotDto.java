/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 可约号源卡片 DTO（4.1）。
 */
@Getter
@Setter
public class BookableSlotDto {

    /** 号源主键。 */
    private Long slotId;

    /** 日程主键。 */
    private Long scheduleId;

    /** 开始时刻。 */
    private LocalDateTime startTime;

    /** 结束时刻。 */
    private LocalDateTime endTime;

    /** 容量。 */
    private Integer capacity;

    /** 已约数。 */
    private Integer bookedCount;

    /** 剩余可约数。 */
    private Integer remainingCount;

    /** 是否已满。 */
    private Boolean fullFlag;

    /** 虚拟号源键（2.25 fallback_bookable）。 */
    private String virtualSlotKey;

    /** 是否来自模板态。 */
    private Boolean fromTemplate;

    /** 是否可加入候补（已满号源）。 */
    private Boolean waitlistEligible;

    /** 当前候补排队人数。 */
    private Long waitlistCount;
}
