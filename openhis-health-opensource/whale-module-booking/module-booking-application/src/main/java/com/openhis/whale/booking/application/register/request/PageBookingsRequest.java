/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 预约列表分页请求（4.5）。
 */
@Getter
@Setter
public class PageBookingsRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 患者主键（可选）。 */
    private Long patientId;

    /** 日期起（可选）。 */
    private LocalDate dateFrom;

    /** 日期止（可选）。 */
    private LocalDate dateTo;

    /** 业务单号关键字（可选）。 */
    private String keyword;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 页大小。 */
    @NotNull
    private Integer pageSize;
}
