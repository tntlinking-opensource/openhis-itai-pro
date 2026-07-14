/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.policy.entity;

import com.openhis.whale.booking.domain.share.RegistrationWalkInSlotModeConstants;
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

    /** 机构标识；空=租户默认。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 现场取号模式：WALK_IN_WITH_SLOT / APPOINTMENT_FIRST / WALK_IN_NO_SLOT 等。 */
    @Column(name = "walk_in_slot_mode", nullable = false)
    private String walkInSlotMode = RegistrationWalkInSlotModeConstants.WALK_IN_WITH_SLOT;

    /** 挂号时是否自动签到。 */
    @Column(name = "auto_check_in_on_register", nullable = false)
    private Boolean autoCheckInOnRegister = Boolean.FALSE;

    /** 取消预约处置：RELEASE_SLOT_ONLY / KEEP_SLOT 等。 */
    @Column(name = "on_cancel_policy", nullable = false)
    private String onCancelPolicy = "RELEASE_SLOT_ONLY";

    /** 是否允许从预约修改接诊医师。 */
    @Column(name = "allow_edit_practitioner_from_appointment", nullable = false)
    private Boolean allowEditPractitionerFromAppointment = Boolean.FALSE;
}
