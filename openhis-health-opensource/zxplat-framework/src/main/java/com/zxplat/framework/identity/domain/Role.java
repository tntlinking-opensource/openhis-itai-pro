/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.domain;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.framework.core.domain.IMultiTenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色实体
 */
@Entity
@Table(name = "zxplat_roles", indexes = {
    @Index(name = "idx_roles_name", columnList = "name"),
    @Index(name = "idx_roles_code", columnList = "code"),
    @Index(name = "idx_roles_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
public class Role extends BaseAuditedEntity implements IMultiTenant {

    @Column(name = "name", nullable = false, unique = true, length = 256)
    private String name;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(name = "code", length = 256)
    private String code;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "is_static", nullable = false)
    private Boolean isStatic = false;

    @Column(name = "tenant_id")
    private Long tenantId;
}
