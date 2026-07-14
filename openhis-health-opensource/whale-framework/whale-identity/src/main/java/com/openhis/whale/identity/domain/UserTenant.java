/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.domain;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户租户关联实体
 * 用于支持一个用户可以属于多个租户的场景
 */
@Entity
@Table(name = "whale_user_tenants", indexes = {
    @Index(name = "idx_ut_user_id", columnList = "user_id"),
    @Index(name = "idx_ut_tenant_id", columnList = "tenant_id"),
    @Index(name = "uk_ut_user_tenant", columnList = "user_id,tenant_id", unique = true)
})
@Getter
@Setter
public class UserTenant extends BaseAuditedEntity {

    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 租户ID
     */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /**
     * 用户（关联）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
}
