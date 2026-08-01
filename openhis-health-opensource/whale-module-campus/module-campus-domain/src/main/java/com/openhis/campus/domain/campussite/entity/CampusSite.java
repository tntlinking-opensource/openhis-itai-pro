/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.campussite.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 校区主数据（cmp_campus_site）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_campus_site")
public class CampusSite extends BaseAuditedEntity {

    @Column(name = "campus_code", nullable = false, length = 64)
    private String campusCode;

    @Column(name = "campus_name", nullable = false, length = 128)
    private String campusName;

    @Column(name = "short_name", length = 64)
    private String shortName;

    @Column(name = "address", length = 512)
    private String address;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = Boolean.FALSE;

    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag = Boolean.TRUE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "remark", length = 512)
    private String remark;
}
