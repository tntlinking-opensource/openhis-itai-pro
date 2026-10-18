/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 日程外部标识（DM-WKF-009 §3.3）。
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_schedule_identifier")
public class ScheduleIdentifier extends BaseAuditedEntity {

    @Column(name = "schedule_id", nullable = false)
    /** 排班主键。 */
    private Long scheduleId;

    @Column(name = "id_system", nullable = false)
    /** 标识system。 */
    private String idSystem;

    @Column(name = "id_value", nullable = false)
    /** 标识value。 */
    private String idValue;

    @Column(name = "use_code")
    /** use编码。 */
    private String useCode;
}
