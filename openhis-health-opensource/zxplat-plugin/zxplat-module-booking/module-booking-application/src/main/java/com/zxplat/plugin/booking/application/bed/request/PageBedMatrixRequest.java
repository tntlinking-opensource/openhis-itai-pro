/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 床位矩阵查询请求。
 */
@Getter
@Setter
public class PageBedMatrixRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 病区地点主键。 */
    @NotNull
    private Long wardLocationId;

    /** 日期区间起始。 */
    @NotNull
    private LocalDate rangeStart;

    /** 日期区间结束。 */
    @NotNull
    private LocalDate rangeEnd;
}
