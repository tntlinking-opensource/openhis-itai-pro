/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 异步开票任务实体（表 {@code einv_boss_issue_task}）。
 *
 * <p>每张 invoice 仅允许一条活跃任务（唯一索引 {@code uk_einv_task_invoice_active}）。
 * 由 {@link com.openhis.whale.einvoice.boss.application.internal.issue.BossIssueScheduler} 轮询
 * PENDING/退避到期的 FAILED，{@link com.openhis.whale.einvoice.boss.application.internal.issue.BossIssueWorkerService} 消费。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "einv_boss_issue_task")
public class EinvBossIssueTask extends BaseAuditedEntity {

    /** 关联 health 票据主键。 */
    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    /** 关联 boss 单据主键。 */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 任务状态（PENDING/PROCESSING/SUCCESS/FAILED）。 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 已尝试次数。 */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    /** 下次重试时间。 */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** 最近一次错误码。 */
    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    /** 最近一次错误信息。 */
    @Column(name = "last_error_msg", length = 1000)
    private String lastErrorMsg;

    /** 触发来源（EVENT/MANUAL/SCANNER）。 */
    @Column(name = "trigger_source", nullable = false, length = 20)
    private String triggerSource;
}
