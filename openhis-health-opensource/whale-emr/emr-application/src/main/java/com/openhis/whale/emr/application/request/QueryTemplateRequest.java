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

package com.openhis.whale.emr.application.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 模板分页查询请求
 */
@Getter
@Setter
public class QueryTemplateRequest {

    private String documentTypeCode;
    /** PERSONAL | DEPARTMENT | HOSPITAL */
    private String scopeLevel;
    private String encounterClassCode;
    private Long organizationId;
    private Boolean enabled;
    private String searchKey;
    private String keyword;
    private Integer pageNo;
    private Integer pageIndex = 1;
    private Integer pageSize = 20;
}
