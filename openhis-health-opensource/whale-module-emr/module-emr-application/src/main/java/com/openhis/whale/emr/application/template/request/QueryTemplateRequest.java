/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 模板分页查询请求
 */
@Getter
@Setter
public class QueryTemplateRequest {

    /** 文书类型编码 */
    private String documentTypeCode;
    /** PERSONAL | DEPARTMENT | HOSPITAL */
    private String scopeLevel;
    /** 适用就诊类别 code：AMB/IMP/OBSENC */
    private String encounterClassCode;
    /** 适用科室/机构 id */
    private Long organizationId;
    /** 是否启用 */
    private Boolean enabled;
    /** 搜索关键字（与 keyword 二选一） */
    private String searchKey;
    /** 关键字（匹配名称/编码等） */
    private String keyword;
    /** 结构项所在页码（分页文书） */
    private Integer pageNo;
    /** 页码别名，1-based */
    private Integer pageIndex = 1;
    /** 每页条数 */
    private Integer pageSize = 20;
}
