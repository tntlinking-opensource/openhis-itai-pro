/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.qc.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 质控执行批次汇总（{@code mrms_qc_run}），单次规则集执行一条。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_qc_run")
public class QcRun extends BaseAuditedEntity {

    /** 关联病案主档 {@code mrms_case_record.id}。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 触发来源 code（{@link com.openhis.health.domain.share.enums.QcTrigger}）。 */
    @Column(name = "trigger_code", nullable = false, length = 32)
    private String triggerCode;

    /** 本次执行 open 级缺陷数。 */
    @Column(name = "defect_open_count", nullable = false)
    private Integer defectOpenCount;

    /** 本次执行 warn 级缺陷数。 */
    @Column(name = "defect_warn_count", nullable = false)
    private Integer defectWarnCount;

    /** 是否通过（无阻断级 open 缺陷）。 */
    @Column(name = "passed", nullable = false)
    private Boolean passed;

    /** 规则集执行完成时间（与 {@code mrms_qc_run} 批次一一对应）。 */
    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;
}
