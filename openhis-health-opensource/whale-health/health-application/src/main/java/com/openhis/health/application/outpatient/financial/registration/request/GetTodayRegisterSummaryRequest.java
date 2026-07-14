/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 当日挂号统计请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "当日挂号统计请求")
public class GetTodayRegisterSummaryRequest {

    /** 统计日期，默认当天。 */
    @Schema(description = "统计日期，默认当天")
    private LocalDate date;
}
