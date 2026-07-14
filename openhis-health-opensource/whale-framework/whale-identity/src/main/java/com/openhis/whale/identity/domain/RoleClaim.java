/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.domain;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色声明实体
 */
@Entity
@Table(name = "whale_role_claims", indexes = {
    @Index(name = "idx_rc_role_id", columnList = "role_id"),
    @Index(name = "idx_rc_claim_type", columnList = "claim_type"),
    @Index(name = "idx_rc_tenant_id", columnList = "tenant_id"),
    @Index(name = "uk_rc_role_type_value", columnList = "role_id,claim_type,claim_value", unique = true)
})
@Getter
@Setter
public class RoleClaim extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 角色ID
     */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /**
     * 声明类型
     */
    @Column(name = "claim_type", nullable = false, length = 256)
    private String claimType;

    /**
     * 声明值
     */
    @Column(name = "claim_value", length = 512)
    private String claimValue;

    /**
     * 租户ID
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    /**
     * 角色（关联）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;
}
