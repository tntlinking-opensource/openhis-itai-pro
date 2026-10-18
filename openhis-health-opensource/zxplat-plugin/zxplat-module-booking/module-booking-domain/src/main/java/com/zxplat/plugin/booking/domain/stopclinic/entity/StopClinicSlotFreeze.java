/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.stopclinic.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 停诊预览源号源冻结（bkg_stop_clinic_slot_freeze，07 §4.2）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_stop_clinic_slot_freeze")
public class StopClinicSlotFreeze extends BaseAuditedEntity {

    /** 机构标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 被停诊日程主键。 */
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    /** 被冻结的源号源主键。 */
    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    /** 冻结过期时间（TTL 自动释放）。 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
