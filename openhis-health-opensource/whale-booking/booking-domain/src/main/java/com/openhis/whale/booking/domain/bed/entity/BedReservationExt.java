package com.openhis.whale.booking.domain.bed.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 床位预留扩展（bkg_bed_reservation_ext）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_bed_reservation_ext")
public class BedReservationExt extends BaseAuditedEntity {

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "ward_location_id")
    private Long wardLocationId;

    @Column(name = "intended_admission_date")
    private LocalDate intendedAdmissionDate;

    @Column(name = "remark")
    private String remark;
}
