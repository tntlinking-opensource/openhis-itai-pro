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
 * 权限实体
 */
@Entity
@Table(name = "whale_permissions", indexes = {
    @Index(name = "idx_permissions_name", columnList = "name"),
    @Index(name = "idx_permissions_parent_name", columnList = "parent_name"),
    @Index(name = "idx_permissions_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
public class Permission extends BaseAuditedEntity implements IMultiTenant {

    @Column(name = "name", nullable = false, unique = true, length = 256)
    private String name;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(name = "parent_name", length = 256)
    private String parentName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tenant_id")
    private Long tenantId;
}
