/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.terminology.entity;

import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.framework.core.domain.IMultiTenant;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

/**
 * 编码系统（CodeSystem）
 *
 * <p>定义一套编码体系，如：药品分类系统、诊疗科目系统等</p>
 *
 * <p>FHIR对应：CodeSystem Resource</p>
 *
 */
@Entity
@Table(name = "ter_code_system", indexes = {
    @Index(name = "idx_code_system_name", columnList = "name", unique = true),
    @Index(name = "idx_code_system_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_code_system_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSystem extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 编码系统名称（对应原来的dictType，唯一标识符）
     * 例如：medication_category_code
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * 编码系统标题（人类可读）
     * 例如：药品分类编码系统
     */
    @Column(name = "title", length = 200)
    private String title;

    /**
     * 描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 版本号（可选）
     * 格式：1.0, 1.1, 2.0 等
     * 如果不需要版本管理，可以设为 null 或 "1.0"
     */
    @Column(name = "version", length = 50)
    @Builder.Default
    private String version = "1.0";

    /**
     * 状态：draft（草稿）、active（活跃）、retired（已废弃）
     */
    @Column(name = "status")
    @Builder.Default
    private String status = PublicationStatus.ACTIVE.getCode();

    /**
     * 租户ID
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    /**
     * 编码概念列表（对应原来的dictData）
     */
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_system_id", referencedColumnName = "id")
    private List<Concept> concepts;
}
