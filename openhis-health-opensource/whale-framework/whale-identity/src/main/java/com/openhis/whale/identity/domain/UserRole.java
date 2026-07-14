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
 * 用户角色关联实体
 */
@Entity
@Table(name = "whale_user_roles", indexes = {
    @Index(name = "idx_user_roles_user_id", columnList = "user_id"),
    @Index(name = "idx_user_roles_role_id", columnList = "role_id"),
    @Index(name = "idx_user_roles_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
public class UserRole extends BaseAuditedEntity implements IMultiTenant {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "tenant_id")
    private Long tenantId;
}
