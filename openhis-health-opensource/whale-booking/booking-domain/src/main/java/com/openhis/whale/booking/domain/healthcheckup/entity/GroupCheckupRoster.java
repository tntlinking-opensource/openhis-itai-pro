package com.openhis.whale.booking.domain.healthcheckup.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检花名册（bkg_group_checkup_roster）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_group_checkup_roster")
public class GroupCheckupRoster extends BaseAuditedEntity {

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "id_card_no")
    private String idCardNo;

    @Column(name = "student_no")
    private String studentNo;

    @Column(name = "mobile_phone")
    private String mobilePhone;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "booking_status_code", nullable = false)
    private String bookingStatusCode = "PENDING";

    @Column(name = "import_status_code", nullable = false)
    private String importStatusCode = "IMPORTED";
}
