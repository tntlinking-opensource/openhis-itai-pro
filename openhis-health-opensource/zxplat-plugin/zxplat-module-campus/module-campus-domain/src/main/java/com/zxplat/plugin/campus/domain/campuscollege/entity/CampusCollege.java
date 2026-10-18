/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.campuscollege.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 学院/部主数据（cmp_campus_college）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_campus_college")
public class CampusCollege extends BaseAuditedEntity {

    @Column(name = "college_code", nullable = false, length = 64)
    private String collegeCode;

    @Column(name = "college_name", nullable = false, length = 128)
    private String collegeName;

    @Column(name = "external_college_id", length = 64)
    private String externalCollegeId;

    @Column(name = "campus_site_id")
    private Long campusSiteId;

    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag = Boolean.TRUE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "remark", length = 512)
    private String remark;
}
