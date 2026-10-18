/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 可约时段 DTO（C 端/窗口选时段）。
 */
@Getter
@Setter
public class HcBookableSlotDto {

    /** 时段配额主键。 */
    private Long daySlotId;

    /** 开始时刻。 */
    private LocalTime startTime;

    /** 结束时刻。 */
    private LocalTime endTime;

    /** 校区编码。 */
    private String campusCode;

    /** 剩余可约。 */
    private Integer remaining;
}
