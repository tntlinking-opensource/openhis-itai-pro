/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 团检单位树节点 DTO。
 */
@Getter
@Setter
public class CompanyTreeNodeDto {

    /** 单位主键。 */
    private Long companyId;

    /** 上级单位主键。 */
    private Long parentId;

    /** 单位名称。 */
    private String name;

    /** 体检中心机构主键。 */
    private Long orgId;

    /** 关联校区学院主键。 */
    private Long campusCollegeId;

    /** 联系人姓名。 */
    private String contactName;

    /** 联系人电话。 */
    private String contactPhone;

    /** 是否存在下级节点。 */
    private Boolean hasChildren;

    /** 子节点列表。 */
    private List<CompanyTreeNodeDto> children = new ArrayList<>();
}
