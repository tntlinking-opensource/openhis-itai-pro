/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 复制日计划请求。
 */
@Getter
@Setter
public class CopyHcDayPlanRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 源日期。 */
    @NotNull
    private LocalDate sourceDate;

    /** 目标日期列表。 */
    @NotNull
    private List<LocalDate> targetDates;
}
