/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.tenant.domain;

import com.zxplat.framework.core.domain.AuditedEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户实体
 *
 * <p>租户管理模块的核心实体，用于管理多租户系统中的租户信息</p>
 *
 * @author OpenHIS Contributors
 */
@Entity
@Table(name = "zxplat_tenants", indexes = {
    @Index(name = "idx_tenants_name", columnList = "name"),
    @Index(name = "idx_tenants_code", columnList = "code"),
    @Index(name = "idx_tenants_is_active", columnList = "is_active")
})
@Getter
@Setter
public class Tenant extends AuditedEntityBase {

    /**
     * 租户名称
     */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * 租户代码（唯一标识）
     */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /**
     * 租户描述
     */
    @Column(name = "description", length = 512)
    private String description;

    /**
     * 是否激活
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * 扩展属性（JSON格式）
     */
    @Column(name = "extra_properties", columnDefinition = "TEXT")
    private String extraProperties;
}
