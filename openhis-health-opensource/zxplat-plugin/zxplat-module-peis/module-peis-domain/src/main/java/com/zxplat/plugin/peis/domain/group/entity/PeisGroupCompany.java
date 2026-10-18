/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.group.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "peis_group_company")
public class PeisGroupCompany extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    /** 上级单位节点；空表示根节点。 */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "campus_college_id")
    private Long campusCollegeId;

    @Column(name = "contact_name", length = 64)
    private String contactName;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;
}
