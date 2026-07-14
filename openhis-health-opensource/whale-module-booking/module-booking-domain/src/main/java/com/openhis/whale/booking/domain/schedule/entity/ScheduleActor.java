/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 日程参与者（DM-WKF-009 §3.2）。
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_schedule_actor")
public class ScheduleActor extends BaseAuditedEntity {

    @Column(name = "schedule_id", nullable = false)
    /** 排班主键。 */
    private Long scheduleId;

    @Column(name = "actor_type", nullable = false)
    /** 参与者类型。 */
    private Integer actorType;

    @Column(name = "actor_id", nullable = false)
    /** 参与者主键。 */
    private Long actorId;

    @Column(name = "sort_no", nullable = false)
    /** sort号。 */
    private Integer sortNo;
}
