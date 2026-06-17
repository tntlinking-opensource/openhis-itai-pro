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
 * 电子病历模板实体。表 emr_template。
 */
@Getter
@Setter
@Entity
@Table(name = "emr_template")
public class Template extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 模板名称
     */
    @Size(max = 128)
    @Column(name = "name", length = 128)
    private String name;

    /**
     * 模板编码，唯一
     */
    @Size(max = 64)
    @Column(name = "code", length = 64, unique = true)
    private String code;

    /**
     * 关联文书类型编码
     */
    @Size(max = 32)
    @Column(name = "document_type_code", length = 32)
    private String documentTypeCode;

    /**
     * 使用范围：PERSONAL / DEPARTMENT / HOSPITAL
     */
    @Size(max = 16)
    @Column(name = "scope_level", length = 16, nullable = false)
    private String scopeLevel = "HOSPITAL";

    /**
     * 个人模板所有者（医务人员 ID）；scope_level=PERSONAL 时必填。
     */
    @Column(name = "owner_practitioner_id")
    private Long ownerPractitionerId;

    /**
     * 适用科室 ID（organizationId）；scope_level=DEPARTMENT 时必填。
     */
    @Column(name = "organization_id")
    private Long organizationId;

    /**
     * 适用就诊类别（与 health EncounterClass.code 一致）：AMB / IMP / OBSENC 等。
     */
    @Size(max = 16)
    @Column(name = "encounter_class_code", length = 16)
    private String encounterClassCode;

    /**
     * 适用就诊类型（遗留整型字段，新数据请用 encounter_class_code）。
     */
    @Column(name = "encounter_class_enum")
    private Integer encounterClassEnum;

    /**
     * 适用就诊子类型（Encounter.typeEnum 等），如普通门诊/专家门诊等。
     * 为空表示所有就诊子类型可见。
     */
    @Column(name = "encounter_type_enum")
    private Integer encounterTypeEnum;

    /**
     * 模板内容 JSON（区域/数据组/数据元结构）
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 是否启用
     */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}

