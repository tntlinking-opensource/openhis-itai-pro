/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * TW 周模板明细：weekday → T2 日模板（bkg_week_schedule_template_item）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_week_schedule_template_item")
public class WeekScheduleTemplateItem extends BaseAuditedEntity {

    /** 周模板头主键。 */
    @Column(name = "week_schedule_template_id", nullable = false)
    private Long weekScheduleTemplateId;

    /** 1=周一 … 7=周日（ISO）。 */
    @Column(name = "weekday", nullable = false)
    private Integer weekday;

    /** 关联 T2 日模板。 */
    @Column(name = "day_schedule_template_id", nullable = false)
    private Long dayScheduleTemplateId;

    /** 同日多条排序。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    /** 是否启用。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
