/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.domain;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限授予实体
 * provider_name: R=角色, U=用户, OU=组织单元
 */
@Entity
@Table(name = "whale_permission_grants", indexes = {
    @Index(name = "idx_permission_grants_permission_name", columnList = "permission_name"),
    @Index(name = "idx_permission_grants_provider", columnList = "provider_name, provider_key"),
    @Index(name = "idx_permission_grants_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
public class PermissionGrant extends BaseAuditedEntity implements IMultiTenant {

    @Column(name = "permission_name", nullable = false, length = 256)
    private String permissionName;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;  // R=角色, U=用户, OU=组织单元

    @Column(name = "provider_key", nullable = false, length = 256)
    private String providerKey;  // 角色ID、用户ID等

    @Column(name = "tenant_id")
    private Long tenantId;
}
