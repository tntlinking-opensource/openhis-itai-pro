/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.audit.application.dto;

import com.zxplat.framework.core.application.dto.BaseQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审计日志查询请求
 * startTime/endTime 使用 LocalDateTime，前端需传无时区格式（如 2026-02-03T00:00:00）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogQueryRequest extends BaseQueryRequest {

    /** 实体类型（类名） */
    private String entityType;
    /** 实体 ID */
    private Long entityId;
    /** 操作类型 */
    private com.zxplat.framework.core.infrastructure.auditing.OperationType operationType;
    /** 用户 ID */
    private Long userId;
    /** 租户 ID */
    private Long tenantId;
    /** 开始时间（无时区，如 2026-02-03T00:00:00） */
    private LocalDateTime startTime;
    /** 结束时间（无时区） */
    private LocalDateTime endTime;
}
