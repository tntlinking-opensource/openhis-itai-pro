/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 当日挂号统计响应（不含待收费 PLANNED）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "当日挂号统计响应")
public class GetTodayRegisterSummaryResponse {

    /** 统计日期。 */
    @Schema(description = "统计日期")
    private LocalDate date;

    /** 当日挂号总数（不含待收费）。 */
    @Schema(description = "当日挂号总数（不含待收费）")
    private Long totalCount;

    /** 当日已退号数。 */
    @Schema(description = "当日已退号数")
    private Long cancelledCount;

    /** 当日有效挂号数（总数减已退）。 */
    @Schema(description = "当日有效挂号数（总数减已退）")
    private Long activeCount;
}
