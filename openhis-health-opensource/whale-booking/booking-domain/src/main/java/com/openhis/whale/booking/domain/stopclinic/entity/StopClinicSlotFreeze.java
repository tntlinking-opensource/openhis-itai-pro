package com.openhis.whale.booking.domain.stopclinic.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
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

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
