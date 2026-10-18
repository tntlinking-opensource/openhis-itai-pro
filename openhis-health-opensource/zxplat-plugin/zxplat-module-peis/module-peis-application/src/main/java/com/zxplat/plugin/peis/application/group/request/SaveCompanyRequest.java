/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检单位请求（GRP-01）。
 */
@Getter
@Setter
public class SaveCompanyRequest {

    /** 单位主键（空则新建）。 */
    private Long companyId;

    /** 单位名称。 */
    private String name;

    /** 体检中心机构主键。 */
    private Long orgId;

    /** 上级单位主键（空为根节点）。 */
    private Long parentId;

    /** 关联校区学院主键（可选）。 */
    private Long campusCollegeId;

    /** 联系人姓名（可选）。 */
    private String contactName;

    /** 联系人电话（可选）。 */
    private String contactPhone;
}
