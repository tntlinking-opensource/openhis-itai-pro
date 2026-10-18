/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.bed.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
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

    /** 关联预约主键（wkf_appointment_queue）。 */
    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    /** 床位/房间 Location 主键。 */
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    /** 患者主键。 */
    @Column(name = "patient_id")
    private Long patientId;

    /** 病区 Location 主键。 */
    @Column(name = "ward_location_id")
    private Long wardLocationId;

    /** 拟入院日期。 */
    @Column(name = "intended_admission_date")
    private LocalDate intendedAdmissionDate;

    /** 备注。 */
    @Column(name = "remark")
    private String remark;
}
