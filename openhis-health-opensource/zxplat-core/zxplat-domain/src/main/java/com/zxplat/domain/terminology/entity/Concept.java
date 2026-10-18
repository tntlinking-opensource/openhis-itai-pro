/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.terminology.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

/**
 * 编码概念（Concept）
 *
 * <p>编码系统中的单个编码项，对应原来的dictData</p>
 *
 * <p>FHIR对应：CodeSystem.concept</p>
 *
 */
@Entity
@Table(name = "ter_concept", indexes = {
    @Index(name = "idx_concept_code_system_code", columnList = "code_system_id, code", unique = true),
    @Index(name = "idx_concept_code", columnList = "code"),
    @Index(name = "idx_concept_parent", columnList = "parent_id"),
    @Index(name = "idx_concept_is_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Concept extends BaseAuditedEntity {

    /**
     * 所属编码系统
     */
    @Column(name = "code_system_id", nullable = false, length = 100)
    private Long codeSystemId;

    /**
     * 编码（唯一标识，对应原来的dictValue）
     * 例如：01, 02, A01.01
     */
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    /**
     * 显示名称（对应原来的dictLabel）
     * 例如：中药饮片、中成药
     */
    @Column(name = "display", nullable = false, length = 500)
    private String display;

    /**
     * 定义（详细描述，可选）
     */
    @Column(name = "definition", columnDefinition = "TEXT")
    private String definition;

    /**
     * 父概念（支持层级结构，可选）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Concept parent;

    /**
     * 子概念列表
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Concept> children;

    /**
     * 排序号（对应原来的dictSort）
     */
    @Column(name = "sort_order")
    private Long sortOrder;

    /**
     * 是否启用（对应原来的status）
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * CSS样式类（对应原来的cssClass）
     */
    @Column(name = "css_class", length = 100)
    private String cssClass;

    /**
     * 列表样式类（对应原来的listClass）
     */
    @Column(name = "list_class", length = 100)
    private String listClass;

    /**
     * 是否默认（对应原来的isDefault）
     * Y=是, N=否
     */
    @Column(name = "is_default", length = 1)
    private String isDefault;
}
