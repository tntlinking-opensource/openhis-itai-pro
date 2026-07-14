/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
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

    /** 所属批次主键。 */
    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** 患者主键（建档后回填）。 */
    @Column(name = "patient_id")
    private Long patientId;

    /** 姓名。 */
    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** 身份证号。 */
    @Column(name = "id_card_no")
    private String idCardNo;

    /** 学号/工号。 */
    @Column(name = "student_no")
    private String studentNo;

    /** 手机号。 */
    @Column(name = "mobile_phone")
    private String mobilePhone;

    /** 关联预约主键。 */
    @Column(name = "appointment_id")
    private Long appointmentId;

    /** PENDING / BOOKED / CANCELLED 等。 */
    @Column(name = "booking_status_code", nullable = false)
    private String bookingStatusCode = "PENDING";

    /** 导入状态。 */
    @Column(name = "import_status_code", nullable = false)
    private String importStatusCode = "IMPORTED";
}
