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
 * 日时段配额 DTO。
 */
@Getter
@Setter
public class HcDaySlotDto {

    /** 主键。 */
    private Long id;

    /** 日计划主键。 */
    private Long dayPlanId;

    /** 计划日期。 */
    private LocalDate planDate;

    /** 开始时刻。 */
    private LocalTime startTime;

    /** 结束时刻。 */
    private LocalTime endTime;

    /** 合同类型编码。 */
    private String contractTypeCode;

    /** 校区编码。 */
    private String campusCode;

    /** 容量。 */
    private Integer capacity;

    /** 已约人数。 */
    private Integer bookedCount;

    /** 剩余可约。 */
    private Integer remaining;

    /** 是否开放。 */
    private Boolean openFlag;
}
