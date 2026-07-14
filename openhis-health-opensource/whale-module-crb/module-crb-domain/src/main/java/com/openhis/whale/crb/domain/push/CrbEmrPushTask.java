/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.push;

import com.openhis.whale.crb.domain.support.CrbTenantRowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * EMR 推送待办队列（crb_emr_push_task）。
 */
@Entity
@Table(name = "crb_emr_push_task")
@Getter
@Setter
public class CrbEmrPushTask extends CrbTenantRowEntity {

    /** receive 路径段。 */
    @Column(name = "resource", nullable = false, length = 64)
    private String resource;

    /** 幂等业务键。 */
    @Column(name = "business_key", nullable = false, length = 256)
    private String businessKey;

    /** SLA 分档 code。 */
    @Column(name = "sla_tier", nullable = false, length = 32)
    private String slaTier;

    /** UPSERT / DELETE。 */
    @Column(name = "operation", nullable = false, length = 16)
    private String operation;

    /** SLA 截止时刻。 */
    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    /** PENDING / SENT / FAILED / DEAD。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 已尝试次数。 */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    /** 业务完成时刻。 */
    @Column(name = "operation_time")
    private LocalDateTime operationTime;

    /** health 侧源实体 id，Worker 加载快照用。 */
    @Column(name = "source_id")
    private Long sourceId;

    /** 操作人 id 字符串。 */
    @Column(name = "operator_id", length = 64)
    private String operatorId;

    /** 最近一次错误摘要。 */
    @Column(name = "last_error", length = 512)
    private String lastError;
}
