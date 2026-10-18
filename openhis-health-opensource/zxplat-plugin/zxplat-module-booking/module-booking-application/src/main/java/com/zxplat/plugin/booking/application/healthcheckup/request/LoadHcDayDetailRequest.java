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
 * 加载日明细请求。
 */
@Getter
@Setter
public class LoadHcDayDetailRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 计划日期。 */
    @NotNull
    private LocalDate planDate;
}
