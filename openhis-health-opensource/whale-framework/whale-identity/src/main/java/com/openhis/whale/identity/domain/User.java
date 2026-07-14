/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openhis.whale.core.application.util.JsonUtils;
import com.openhis.whale.core.domain.AuditedEntityBase;
import com.openhis.whale.core.domain.IAuditSummary;
import com.openhis.whale.core.infrastructure.auditing.OperationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户实体（不与 {@code whale_users.tenant_id} 绑定；租户关系见 {@link UserTenant} / {@code whale_user_tenants}）。
 */
@Entity
@Table(name = "whale_users", indexes = {
    @Index(name = "idx_users_user_name", columnList = "user_name"),
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_ukey_id", columnList = "ukey_id"),
    @Index(name = "idx_users_ukey_src_id", columnList = "ukey_src_id")
})
@Getter
@Setter
public class User extends AuditedEntityBase implements IAuditSummary {

    @Column(name = "user_name", nullable = false, length = 256)
    private String userName;

    @Column(name = "name", length = 64)
    private String name;

    @Column(name = "surname", length = 64)
    private String surname;

    @Column(name = "email", length = 256)
    private String email;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false, length = 256)
    @JsonIgnore  // 审计/序列化时排除，避免写入日志或 API
    private String passwordHash;

    /** 启用标志，语义等同 {@code PublicationStatus.ACTIVE}（identity 层无 status_enum 列）。 */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_locked_out", nullable = false)
    private Boolean isLockedOut = false;

    @Column(name = "lockout_end")
    private LocalDateTime lockoutEnd;

    @Column(name = "access_failed_count", nullable = false)
    private Integer accessFailedCount = 0;

    @Column(name = "ukey_id", length = 128)
    private String ukeyId;

    @Column(name = "ukey_src_id", length = 128)
    private String ukeySrcId;

    @Column(name = "extra_properties")
    @JdbcTypeCode(SqlTypes.JSON)
    private String extraProperties;  // JSON格式的扩展属性

    @Override
    public String getAuditSummary(OperationType operationType) {
        if (operationType != OperationType.UPDATE) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId());
        m.put("userName", getUserName());
        m.put("updatedAt", getUpdatedAt());
        return JsonUtils.toJson(m);
    }
}
