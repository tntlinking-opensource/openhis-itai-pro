/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 手动新增日时段配额请求。
 */
@Getter
@Setter
public class AddHcDaySlotRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 计划日期。 */
    @NotNull
    private LocalDate planDate;

    /** 开始时刻。 */
    @NotNull
    private LocalTime startTime;

    /** 结束时刻。 */
    @NotNull
    private LocalTime endTime;

    /** 合同类型编码。 */
    @NotNull
    private String contractTypeCode;

    /** 校区编码（校区模块启用时）。 */
    private String campusCode;

    /** 容量。 */
    private Integer capacity;

    /** 是否开放。 */
    private Boolean openFlag;
}
