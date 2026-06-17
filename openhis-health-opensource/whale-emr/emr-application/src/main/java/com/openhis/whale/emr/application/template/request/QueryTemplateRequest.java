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
