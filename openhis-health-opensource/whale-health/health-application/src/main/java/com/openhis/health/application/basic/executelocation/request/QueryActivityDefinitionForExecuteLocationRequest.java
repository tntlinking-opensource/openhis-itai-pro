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
 * 为执行地点配置「挑选诊疗定义」提供的分页查询：走产品定义读模型，限定活动定义表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryActivityDefinitionForExecuteLocationRequest extends AbstractHealthPageRequest {

    /** 执行科室上下文（校验必填，用于接口权限或后续扩展过滤）。 */
    private Long executionOrgId;
    /** 诊疗大类，必填；与读模型 category 过滤一致。 */
    private String activityCategoryCode;
}
