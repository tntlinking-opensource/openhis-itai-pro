/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.archivechangelog.entity;

import com.openhis.whale.core.infrastructure.id.SnowflakeId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 档案字段级变更记录（cmp_archive_change_log，append-only）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_archive_change_log")
public class ArchiveChangeLog {

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

    @Column(name = "subject_type_enum", nullable = false, length = 32)
    private String subjectTypeEnum;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "change_type_enum", nullable = false, length = 32)
    private String changeTypeEnum;

    @Column(name = "field_name", nullable = false, length = 128)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(name = "change_time", nullable = false)
    private LocalDateTime changeTime;

    @Column(name = "change_source_enum", nullable = false, length = 32)
    private String changeSourceEnum;

    @Column(name = "campus_code", length = 64)
    private String campusCode;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "remark", length = 512)
    private String remark;
}
