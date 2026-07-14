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

import java.time.LocalDate;

/**
 * 逾期通知幂等日志（{@code mrms_overdue_notify_log}）；同一病案 + 类型 + 自然日仅通知一次。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_overdue_notify_log")
public class OverdueNotifyLog extends BaseAuditedEntity {

    /** 病案主档主键 {@code mrms_case_record.id}。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 通知类型 code（如 {@code submit_overdue}、{@code archive_overdue}）。 */
    @Column(name = "notify_type_code", nullable = false, length = 32)
    private String notifyTypeCode;

    /** 通知自然日（与 caseRecordId + notifyTypeCode 组成幂等键）。 */
    @Column(name = "notify_date", nullable = false)
    private LocalDate notifyDate;
}
