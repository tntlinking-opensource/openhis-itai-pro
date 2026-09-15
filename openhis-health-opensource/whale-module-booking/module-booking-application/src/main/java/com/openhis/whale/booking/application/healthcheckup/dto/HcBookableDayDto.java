/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 可约日期 DTO（C 端/窗口选日）。
 */
@Getter
@Setter
public class HcBookableDayDto {

    /** 计划日期。 */
    private LocalDate planDate;

    /** 当日剩余可约总量。 */
    private Integer remaining;
}
