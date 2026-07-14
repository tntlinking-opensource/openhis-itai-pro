/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.audit.domain;

import com.openhis.whale.core.infrastructure.auditing.OperationType;
import com.openhis.whale.core.infrastructure.id.SnowflakeId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 * 表 whale_audit_logs，仅追加写入，不继承 BaseAuditedEntity 避免循环审计
 */
@Entity
@Table(name = "whale_audit_logs", indexes = {
        @Index(name = "idx_audit_logs_entity_type", columnList = "entity_type"),
        @Index(name = "idx_audit_logs_entity_id", columnList = "entity_id"),
        @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_logs_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
public class AuditLog {

    @Id
    @SnowflakeId
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 256)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private OperationType operationType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "changes", columnDefinition = "TEXT")
    private String changes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
