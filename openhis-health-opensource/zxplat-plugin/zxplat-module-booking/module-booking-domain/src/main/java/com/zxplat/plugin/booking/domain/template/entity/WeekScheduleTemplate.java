/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.template.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * TW 周排班模板头（bkg_week_schedule_template，07 §5.3b）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_week_schedule_template")
public class WeekScheduleTemplate extends BaseAuditedEntity {

    /** 科室主键；可空=全院。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 模板编码。 */
    @Column(name = "template_code", nullable = false)
    private String templateCode;

    /** 模板名称。 */
    @Column(name = "template_name", nullable = false)
    private String templateName;

    /** 无 Schedule 时模板态是否允许预约（默认 false）。 */
    @Column(name = "fallback_bookable", nullable = false)
    private Boolean fallbackBookable = Boolean.FALSE;

    /** 科室默认周模板。 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    /** 是否启用。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 生效起。 */
    @Column(name = "effective_start")
    private LocalDate effectiveStart;

    /** 生效止。 */
    @Column(name = "effective_end")
    private LocalDate effectiveEnd;
}
