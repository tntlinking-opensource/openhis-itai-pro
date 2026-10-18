/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 门诊预约分页查询入参。
 */
@Getter
@Setter
public class AppointmentPageQuery {

    /** 机构/科室主键。 */
    private Long organizationId;

    /** 患者主键（可选）。 */
    private Long patientId;

    /** 计划开始日期起（含，按 startInstant 落点）。 */
    private LocalDate dateFrom;

    /** 计划开始日期止（含）。 */
    private LocalDate dateTo;

    /** 业务单号关键字（可选）。 */
    private String keyword;

    /** 页码（1-based）。 */
    private Integer pageNo;

    /** 页大小。 */
    private Integer pageSize;
}
