/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.patientstaff.entity;

import com.openhis.campus.domain.shared.CampusDomainConstants;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 教职工档案（cmp_patient_staff）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_patient_staff")
public class PatientStaff extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "py_str", length = 64)
    private String pyStr;

    @Column(name = "wb_str", length = 64)
    private String wbStr;

    @Column(name = "gender_enum", length = 32)
    private String genderEnum;

    @Column(name = "id_card", length = 32)
    private String idCard;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "employee_no", nullable = false, length = 64)
    private String employeeNo;

    @Column(name = "medical_code", length = 64)
    private String medicalCode;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "campus_site_id", nullable = false)
    private Long campusSiteId;

    @Column(name = "staff_type_enum", length = 32)
    private String staffTypeEnum;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "job_status_enum", nullable = false, length = 32)
    private String jobStatusEnum = CampusDomainConstants.JOB_STATUS_ACTIVE;

    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag = Boolean.TRUE;
}
