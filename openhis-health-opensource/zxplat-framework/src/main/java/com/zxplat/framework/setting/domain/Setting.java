/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.setting.domain;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.framework.core.domain.IMultiTenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 设置实体
 *
 * <p>
 * 用于管理应用配置和设置，支持多租户隔离
 * </p>
 *
 * @author OpenHIS Contributors
 */
@Entity
@Table(name = "zxplat_settings",
    indexes = {@Index(name = "idx_settings_name", columnList = "name"),
        @Index(name = "idx_settings_provider_name", columnList = "provider_name"),
        @Index(name = "idx_settings_provider_key", columnList = "provider_key"),
        @Index(name = "idx_settings_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_settings_name_provider", columnList = "name, provider_name, provider_key")})
@Getter
@Setter
public class Setting extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 设置名称（键）
     */
    @Column(name = "name", nullable = false, length = 256)
    private String name;

    /**
     * 设置值
     */
    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

    /**
     * 提供者名称（如：G-全局, U-用户，R-角色，T-租户）
     */
    @Column(name = "provider_name", length = 64)
    private String providerName;

    /**
     * 提供者键（如：用户ID、角色ID、租户ID）
     */
    @Column(name = "provider_key", length = 256)
    private String providerKey;

    /**
     * 租户ID（多租户支持）
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    /**
     * 描述
     */
    @Column(name = "description", length = 512)
    private String description;
}
