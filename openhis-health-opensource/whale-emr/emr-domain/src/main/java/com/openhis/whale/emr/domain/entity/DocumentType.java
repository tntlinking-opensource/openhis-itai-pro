/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.domain.entity;

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
}

