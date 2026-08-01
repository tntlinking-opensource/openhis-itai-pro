/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.document.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 文书类型配置。表 emr_document_type。
 */
@Getter
@Setter
@Entity
@Table(name = "emr_document_type")
public class DocumentType extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 文书类型编码，唯一
     */
    @Size(max = 32)
    @Column(name = "code", length = 32, unique = true, nullable = false)
    private String code;

    /**
     * 文书类型名称
     */
    @Size(max = 64)
    @Column(name = "name", length = 64, nullable = false)
    private String name;

    /**
     * 默认模板 ID
     */
    @Column(name = "default_template_id")
    private Long defaultTemplateId;

    /**
     * 是否启用
     */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /**
     * 排序
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 文书分类编码：OUTPATIENT / INPATIENT / NOTICE / SURGERY / OTHER
     */
    @Size(max = 32)
    @Column(name = "category_code", length = 32)
    private String categoryCode;
}
