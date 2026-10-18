/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.recheck.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 体检复查任务（表 {@code peis_recheck_task}；Peis 04 §4.5 方案 A 本地 Task 语义）。
 */
@Getter
@Setter
@Entity
@Table(name = "peis_recheck_task")
public class PeisRecheckTask extends BaseAuditedEntity {

    /** 机构标识。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 原体检就诊标识。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 患者标识。 */
    @Column(name = "patient_id")
    private Long patientId;

    /** 任务状态（requested/in-progress/completed）。 */
    @Column(name = "status_enum", nullable = false, length = 64)
    private String statusEnum;

    /** 复查原因。 */
    @Column(name = "reason", length = 512)
    private String reason;

    /** 复查完成后关联的总检报告标识。 */
    @Column(name = "diagnostic_report_id")
    private Long diagnosticReportId;

    /** 完成时间。 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 完成操作人。 */
    @Column(name = "completed_by")
    private Long completedBy;
}
