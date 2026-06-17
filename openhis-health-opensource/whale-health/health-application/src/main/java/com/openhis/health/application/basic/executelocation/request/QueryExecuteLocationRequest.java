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

package com.openhis.health.application.basic.executelocation.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 按执行科室分页查询已绑定的执行规则列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryExecuteLocationRequest extends AbstractHealthPageRequest {

    /** 执行科室主键，必填。 */
    private Long executionOrgId;
    /** 诊疗大类过滤；可空表示不按大类过滤（由查询服务解释）。 */
    private String activityCategoryCode;
}
