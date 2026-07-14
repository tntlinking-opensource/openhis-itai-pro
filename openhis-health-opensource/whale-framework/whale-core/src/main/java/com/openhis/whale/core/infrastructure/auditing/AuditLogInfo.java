/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.auditing;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计日志信息 DTO
 * 与存储解耦，由 IAuditingStore 实现方决定持久化方式
 */
@Getter
@Setter
@Builder
public class AuditLogInfo {

    /** 实体类型（类名） */
    private String entityType;
    /** 实体主键 */
    private Long entityId;
    /** 操作类型 */
    private OperationType operationType;
    /** 操作用户 ID */
    private Long userId;
    /** 租户 ID */
    private Long tenantId;
    /** 客户端 IP */
    private String ipAddress;
    /** User-Agent */
    private String userAgent;
    /** 变更前状态（JSON），CREATE 可为 null */
    private String oldState;
    /** 变更后状态（JSON），DELETE 可为 null */
    private String newState;
    /** 发生时间 */
    private LocalDateTime createdAt;
}
