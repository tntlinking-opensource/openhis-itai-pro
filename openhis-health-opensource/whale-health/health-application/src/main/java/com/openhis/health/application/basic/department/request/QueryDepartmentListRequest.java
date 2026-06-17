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

package com.openhis.health.application.basic.department.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 科室管理列表查询请求（POST /department/getDepartmentList）。
 */
@Data
@Schema(description = "科室管理列表查询请求")
public class QueryDepartmentListRequest {

    @Schema(description = "关键字")
    private String keyword;

    @Schema(description = "机构层级类型")
    private String typeEnum;

    /** 机构业务分类 code 或 sortOrder 字符串。 */
    @Schema(description = "机构业务分类")
    private String classEnum;

    /** 发布状态 code（ACTIVE/INACTIVE 等）。 */
    @Schema(description = "发布状态")
    private String statusEnum;
}
