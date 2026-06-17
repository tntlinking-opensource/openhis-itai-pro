package com.openhis.whale.booking.domain.policy.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 院点挂号策略（bkg_registration_policy）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_registration_policy")
public class RegistrationPolicy extends BaseAuditedEntity {

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "walk_in_slot_mode", nullable = false)
    private String walkInSlotMode = "APPOINTMENT_FIRST";

    @Column(name = "auto_check_in_on_register", nullable = false)
    private Boolean autoCheckInOnRegister = Boolean.FALSE;

    @Column(name = "on_cancel_policy", nullable = false)
    private String onCancelPolicy = "RELEASE_SLOT_ONLY";

    @Column(name = "allow_edit_practitioner_from_appointment", nullable = false)
    private Boolean allowEditPractitionerFromAppointment = Boolean.FALSE;
}
