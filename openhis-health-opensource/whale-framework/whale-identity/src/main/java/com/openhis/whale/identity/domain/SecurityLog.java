/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.domain;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IExcludeFromAudit;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 安全日志实体
 */
@Entity
@Table(name = "whale_security_logs", indexes = {
    @Index(name = "idx_security_logs_user_id", columnList = "user_id"),
    @Index(name = "idx_security_logs_action", columnList = "action"),
    @Index(name = "idx_security_logs_created_at", columnList = "created_at"),
    @Index(name = "idx_security_logs_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
public class SecurityLog extends BaseAuditedEntity implements IMultiTenant, IExcludeFromAudit {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name", length = 256)
    private String userName;

    @Column(name = "action", nullable = false, length = 64)
    private String action;  // LOGIN, LOGOUT, PASSWORD_CHANGE, PERMISSION_CHANGE等

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "result", length = 32)
    private String result;  // SUCCESS, FAILED

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "extra_properties")
    @JdbcTypeCode(SqlTypes.JSON)
    private String extraProperties;  // JSON格式的扩展属性
}
