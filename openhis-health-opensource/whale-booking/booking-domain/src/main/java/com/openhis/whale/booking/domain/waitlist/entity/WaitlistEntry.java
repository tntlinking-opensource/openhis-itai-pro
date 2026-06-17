package com.openhis.whale.booking.domain.waitlist.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 号源候补条目（bkg_waitlist_entry，BR-EXT-011）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_waitlist_entry")
public class WaitlistEntry extends BaseAuditedEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "queue_position", nullable = false)
    private Integer queuePosition;

    /** WAITING / PROMOTED / CANCELLED。 */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "promoted_appointment_id")
    private Long promotedAppointmentId;

    @Column(name = "cancel_reason")
    private String cancelReason;
}
