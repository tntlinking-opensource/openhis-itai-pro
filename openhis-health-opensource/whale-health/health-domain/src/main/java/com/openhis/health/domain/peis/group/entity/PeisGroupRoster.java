/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.group.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "peis_group_roster")
public class PeisGroupRoster extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    @Column(name = "id_card_no", length = 32)
    private String idCardNo;

    @Column(name = "student_no", length = 64)
    private String studentNo;

    @Column(name = "employee_no", length = 64)
    private String employeeNo;

    @Column(name = "college_id")
    private Long collegeId;

    @Column(name = "class_name", length = 128)
    private String className;

    @Column(name = "mobile_phone", length = 32)
    private String mobilePhone;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "pay_status_code", nullable = false, length = 32)
    private String payStatusCode = "UNPAID";

    @Column(name = "arrival_status_code", nullable = false, length = 32)
    private String arrivalStatusCode = "PENDING";
}
