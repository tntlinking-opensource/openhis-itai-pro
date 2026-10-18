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
 * 用户与组织单元关联（identity 登录可选科室）。
 *
 * <p>{@link #organizationUnitId} 存 ZXPlat {@code OrganizationUnit.id}，<strong>不是</strong>
 * {@code adm_organization.id}。HIS 侧岗位维护经 {@code IOrganizationUnitScopeBridge} 按 code 转换后写入。</p>
 */
@Entity
@Table(name = "zxplat_user_organization_units", indexes = {
    @Index(name = "idx_uou_user_id", columnList = "user_id"),
    @Index(name = "idx_uou_ou_id", columnList = "organization_unit_id"),
    @Index(name = "idx_uou_tenant_id", columnList = "tenant_id"),
    @Index(name = "uk_uou_user_ou", columnList = "user_id,organization_unit_id", unique = true)
})
@Getter
@Setter
public class UserOrganizationUnit extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 组织单元ID
     */
    @Column(name = "organization_unit_id", nullable = false)
    private Long organizationUnitId;

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

    /**
     * 组织单元（关联）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_unit_id", insertable = false, updatable = false)
    private OrganizationUnit organizationUnit;
}
