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
 * 组织单元实体（ZXPlat identity 组织树）。
 *
 * <p>与 HIS {@code adm_organization} <strong>不共用主键</strong>；可追溯关联键为 {@link #code}：
 * 通常等于组织 {@code busNo}，无 busNo 时为 {@code org-{adm_organization.id}}。
 * 科室增删改由 health 应用层按 code 同步，ZXPlat 侧 id 由基类雪花策略自动生成。</p>
 */
@Entity
@Table(name = "zxplat_organization_units", indexes = {
    @Index(name = "idx_ou_code", columnList = "code"),
    @Index(name = "idx_ou_parent_id", columnList = "parent_id"),
    @Index(name = "idx_ou_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
public class OrganizationUnit extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 组织单元代码（唯一，与 HIS 组织 busNo / org-主键 字符串对齐，非 ZXPlat 主键）
     */
    @Column(name = "code", nullable = false, length = 128, unique = true)
    private String code;

    /**
     * 显示名称
     */
    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    /**
     * 父组织单元 ID（ZXPlat OU 主键，非 adm_organization.id）
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * 排序号
     */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /**
     * 是否启用，语义等同 {@code PublicationStatus.ACTIVE}（见 health-domain-shared）。
     */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    /**
     * 备注
     */
    @Column(name = "remark", length = 512)
    private String remark;

    /**
     * 父组织单元（只读关联）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private OrganizationUnit parent;
}
