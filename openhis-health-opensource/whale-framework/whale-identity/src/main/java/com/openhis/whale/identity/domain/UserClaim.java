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
 * 用户声明实体
 */
@Entity
@Table(name = "whale_user_claims", indexes = {
    @Index(name = "idx_uc_user_id", columnList = "user_id"),
    @Index(name = "idx_uc_claim_type", columnList = "claim_type"),
    @Index(name = "idx_uc_tenant_id", columnList = "tenant_id"),
    @Index(name = "uk_uc_user_type_value", columnList = "user_id,claim_type,claim_value", unique = true)
})
@Getter
@Setter
public class UserClaim extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

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
     * 用户（关联）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
}
