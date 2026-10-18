/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 设置整日休息请求。
 */
@Getter
@Setter
public class SetHcDayRestRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 计划日期。 */
    @NotNull
    private LocalDate planDate;

    /** 是否休息。 */
    @NotNull
    private Boolean restFlag;
}
