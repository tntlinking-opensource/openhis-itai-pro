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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 元数据字段定义。表 emr_metadata_field_def。
 */
@Getter
@Setter
@Entity
@Table(name = "emr_metadata_field_def")
public class MetadataFieldDef extends BaseAuditedEntity implements IMultiTenant {

    @NotBlank
    @Size(max = 64)
    @Column(name = "field_key", length = 64, nullable = false)
    private String fieldKey;

    @NotBlank
    @Size(max = 128)
    @Column(name = "label", length = 128, nullable = false)
    private String label;

    @Size(max = 16)
    @Column(name = "data_type", length = 16, nullable = false)
    private String dataType = "string";

    @Column(name = "editable_default", nullable = false)
    private Boolean editableDefault = Boolean.TRUE;

    @NotBlank
    @Size(max = 32)
    @Column(name = "value_source", length = 32, nullable = false)
    private String valueSource;

    @Size(max = 256)
    @Column(name = "source_path", length = 256)
    private String sourcePath;

    @Column(name = "source_config", columnDefinition = "TEXT")
    private String sourceConfig;

    @Size(max = 512)
    @Column(name = "default_value", length = 512)
    private String defaultValue;

    @Size(max = 32)
    @Column(name = "mask_rule", length = 32, nullable = false)
    private String maskRule = "NONE";

    @Size(max = 64)
    @Column(name = "format_pattern", length = 64)
    private String formatPattern;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Size(max = 512)
    @Column(name = "remark", length = 512)
    private String remark;
}
