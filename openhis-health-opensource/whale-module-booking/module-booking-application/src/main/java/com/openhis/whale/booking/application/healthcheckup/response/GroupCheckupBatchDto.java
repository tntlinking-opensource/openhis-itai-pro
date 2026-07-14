/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 团检批次 DTO（9.4）。
 */
@Getter
@Setter
public class GroupCheckupBatchDto {

    /** 团检批次主键。 */
    private Long id;

    /** 团检单位主键。 */
    private Long orgId;

    /** 团检单位名称。 */
    private String orgName;

    /** 批次名称。 */
    private String batchName;

    /** 体检套餐主键。 */
    private Long packageId;

    /** 日程主键。 */
    private Long scheduleId;

    /** 预约日期。 */
    private LocalDate bookingDate;

    /** 开始时刻。 */
    private LocalTime startTime;

    /** 结束时刻。 */
    private LocalTime endTime;

    /** 容量上限。 */
    private Integer capacityLimit;

    /** 已约人数。 */
    private Integer bookedCount;

    /** 状态编码。 */
    private String statusCode;
}
