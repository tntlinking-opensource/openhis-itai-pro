/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 按周复制排班请求（2.11）。
 */
@Getter
@Setter
public class CopyScheduleToNextWeekRequest {

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;

    /** 源周区间起（含）。 */
    @NotNull
    private LocalDate sourceRangeStart;

    /** 源周区间止（含）。 */
    @NotNull
    private LocalDate sourceRangeEnd;

    /** 目标周区间起（含）。 */
    @NotNull
    private LocalDate targetRangeStart;

    /** 目标周区间止（含）。 */
    @NotNull
    private LocalDate targetRangeEnd;

    /** 是否按源 Schedule 的 Slot 模板重新放号。 */
    private Boolean copySlotProvision;
}
