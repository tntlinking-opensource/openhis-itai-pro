/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.campusdepartment.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 教职工部门/单位（cmp_campus_department）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_campus_department")
public class CampusDepartment extends BaseAuditedEntity {

    @Column(name = "department_code", nullable = false, length = 64)
    private String departmentCode;

    @Column(name = "department_name", nullable = false, length = 128)
    private String departmentName;

    @Column(name = "campus_site_id", nullable = false)
    private Long campusSiteId;

    @Column(name = "college_id")
    private Long collegeId;

    @Column(name = "dept_type_enum", length = 32)
    private String deptTypeEnum;

    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag = Boolean.TRUE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "remark", length = 512)
    private String remark;
}
