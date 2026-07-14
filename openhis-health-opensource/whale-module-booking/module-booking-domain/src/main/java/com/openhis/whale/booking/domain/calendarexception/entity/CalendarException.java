/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.calendarexception.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * T3 日历例外（bkg_calendar_exception，07 §5.4）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_calendar_exception")
public class CalendarException extends BaseAuditedEntity {

    /** 科室主键；空=全院。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 例外日期或区间起点。 */
    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    /** 区间终点；空=单日。 */
    @Column(name = "range_end")
    private LocalDate rangeEnd;

    /** CLOSED / REDUCED / OVERRIDE / EXTRA。 */
    @Column(name = "exception_type", nullable = false)
    private String exceptionType;

    /** REDUCED/OVERRIDE/EXTRA 时关联 T2。 */
    @Column(name = "override_schedule_template_id")
    private Long overrideScheduleTemplateId;

    /** 说明（国庆、调休等）。 */
    @Column(name = "reason")
    private String reason;

    /** 参与从模板生成。 */
    @Column(name = "auto_apply_on_generate", nullable = false)
    private Boolean autoApplyOnGenerate = Boolean.TRUE;
}
