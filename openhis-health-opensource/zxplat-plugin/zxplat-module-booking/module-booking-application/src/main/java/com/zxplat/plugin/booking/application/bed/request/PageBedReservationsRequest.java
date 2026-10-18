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
 * 床位预留列表分页请求。
 */
@Getter
@Setter
public class PageBedReservationsRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 病区地点主键（可选筛选）。 */
    private Long wardLocationId;

    /** 预留日期起（可选筛选）。 */
    private LocalDate dateFrom;

    /** 预留日期止（可选筛选）。 */
    private LocalDate dateTo;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 每页条数。 */
    @NotNull
    private Integer pageSize;
}
