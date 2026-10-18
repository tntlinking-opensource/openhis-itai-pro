/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 月历日摘要 DTO。
 */
@Getter
@Setter
public class HcDaySummaryDto {

    /** 计划日期。 */
    private LocalDate planDate;

    /** 整日休息标记。 */
    private Boolean restFlag;

    /** 总容量。 */
    private Integer totalCapacity;

    /** 总已约。 */
    private Integer totalBooked;

    /** 总剩余。 */
    private Integer remaining;

    /** 日状态：OPEN / REST / FULL / EMPTY。 */
    private String status;
}
