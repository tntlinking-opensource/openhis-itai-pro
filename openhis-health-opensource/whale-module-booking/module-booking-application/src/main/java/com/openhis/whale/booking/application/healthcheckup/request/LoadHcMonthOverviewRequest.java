/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 加载月历总览请求。
 */
@Getter
@Setter
public class LoadHcMonthOverviewRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 年月（与 fromDate/toDate 二选一）。 */
    private YearMonth yearMonth;

    /** 起始日期（含）。 */
    private LocalDate fromDate;

    /** 结束日期（含）。 */
    private LocalDate toDate;
}
