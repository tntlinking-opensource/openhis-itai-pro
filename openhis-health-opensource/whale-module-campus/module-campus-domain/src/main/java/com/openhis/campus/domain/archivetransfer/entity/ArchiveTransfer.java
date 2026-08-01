/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.archivetransfer.entity;

import com.openhis.whale.core.infrastructure.id.SnowflakeId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 档案调动/转院记录（cmp_archive_transfer）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_archive_transfer")
public class ArchiveTransfer {

    @Id
    @SnowflakeId
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "subject_type_enum", nullable = false, length = 32)
    private String subjectTypeEnum;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "transfer_type_enum", nullable = false, length = 32)
    private String transferTypeEnum;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "from_campus_code", length = 64)
    private String fromCampusCode;

    @Column(name = "to_campus_code", length = 64)
    private String toCampusCode;

    @Column(name = "from_college_id")
    private Long fromCollegeId;

    @Column(name = "to_college_id")
    private Long toCollegeId;

    @Column(name = "from_department_id")
    private Long fromDepartmentId;

    @Column(name = "to_department_id")
    private Long toDepartmentId;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "external_ref", length = 128)
    private String externalRef;

    @Column(name = "transfer_source_enum", nullable = false, length = 32)
    private String transferSourceEnum;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(name = "operate_time", nullable = false)
    private LocalDateTime operateTime;

    @Column(name = "change_log_batch_id", length = 64)
    private String changeLogBatchId;
}
