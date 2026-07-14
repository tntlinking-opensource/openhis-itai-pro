/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.audit.application.dto;

import com.openhis.whale.core.infrastructure.auditing.OperationType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志 DTO
 */
@Data
public class AuditLogDto {

    private Long id;
    private String entityType;
    private Long entityId;
    private OperationType operationType;
    private Long userId;
    private Long tenantId;
    private String ipAddress;
    private String userAgent;
    private String changes;
    private LocalDateTime createdAt;
}
